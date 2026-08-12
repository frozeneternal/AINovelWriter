package com.ainovel.app.domain.analysis

import com.ainovel.app.domain.agent.LlmGateway
import com.ainovel.app.domain.agent.PromptTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AnalysisRequest(
    val novelId: Long,
    val fullText: String
)

data class AnalysisResult(
    val chapterCount: Int,
    val characters: String,
    val worldview: String,
    val plotSummary: String,
    val styleProfile: String
)

/**
 * 解析持久化回调：由调用方（Repository 集成层）实现，负责把阶段产物写入数据库。
 */
interface AnalysisPersistence {
    suspend fun saveChapters(novelId: Long, chapters: List<SplitChapter>)
    suspend fun saveCharacters(novelId: Long, charactersText: String)
    suspend fun saveWorldview(novelId: Long, worldviewText: String)
    suspend fun savePlotAndStyle(novelId: Long, plotSummary: String, styleProfile: String)
}

class NovelAnalyzer(
    private val llm: LlmGateway,
    private val persistence: AnalysisPersistence
) {

    fun analyze(
        request: AnalysisRequest,
        session: AnalysisSession
    ): Flow<AnalysisEvent> = flow {

        session.update { it.copy(phase = AnalysisPhase.SPLIT_CHAPTERS, message = "章节切分中…") }
        emit(AnalysisEvent.PhaseStarted(AnalysisPhase.SPLIT_CHAPTERS))
        val chapters = ChapterSplitter.split(request.fullText)
        if (chapters.isEmpty()) {
            session.update {
                it.copy(phase = AnalysisPhase.FAILED, error = "无法解析小说内容（空文本）")
            }
            emit(AnalysisEvent.Error("无法解析小说内容（空文本）"))
            return@flow
        }
        persistence.saveChapters(request.novelId, chapters)
        session.update { it.copy(chapterCount = chapters.size) }
        emit(AnalysisEvent.ChaptersSplit(chapters.size))

        // 人物提取
        session.update { it.copy(phase = AnalysisPhase.CHARACTERS, message = "人物提取中…") }
        emit(AnalysisEvent.PhaseStarted(AnalysisPhase.CHARACTERS))
        val charactersText = try {
            llm.complete(
                systemPrompt = PromptTemplates.analysisAgent("character-extractor").systemPrompt,
                userMessage = chapters.take(20).joinToString("\n\n---\n\n") { it.content }.take(16000),
                temperature = PromptTemplates.analysisAgent("character-extractor").temperature,
                maxTokens = PromptTemplates.analysisAgent("character-extractor").maxTokens
            )
        } catch (e: Exception) {
            session.update { it.copy(phase = AnalysisPhase.FAILED, error = e.message) }
            emit(AnalysisEvent.Error(e.message ?: "人物提取失败"))
            return@flow
        }
        persistence.saveCharacters(request.novelId, charactersText)
        emit(AnalysisEvent.PhaseFinished(AnalysisPhase.CHARACTERS, charactersText))

        // 世界观提取
        session.update { it.copy(phase = AnalysisPhase.WORLDVIEW, message = "世界观提取中…") }
        emit(AnalysisEvent.PhaseStarted(AnalysisPhase.WORLDVIEW))
        val worldviewText = try {
            llm.complete(
                systemPrompt = PromptTemplates.analysisAgent("worldview-extractor").systemPrompt,
                userMessage = chapters.take(30).joinToString("\n\n---\n\n") { it.content }.take(20000),
                temperature = PromptTemplates.analysisAgent("worldview-extractor").temperature,
                maxTokens = PromptTemplates.analysisAgent("worldview-extractor").maxTokens
            )
        } catch (e: Exception) {
            session.update { it.copy(phase = AnalysisPhase.FAILED, error = e.message) }
            emit(AnalysisEvent.Error(e.message ?: "世界观提取失败"))
            return@flow
        }
        persistence.saveWorldview(request.novelId, worldviewText)
        emit(AnalysisEvent.PhaseFinished(AnalysisPhase.WORLDVIEW, worldviewText))

        // 情节梗概 + 手法画像
        session.update { it.copy(phase = AnalysisPhase.PLOT_STYLE, message = "情节与手法分析中…") }
        emit(AnalysisEvent.PhaseStarted(AnalysisPhase.PLOT_STYLE))
        val plotStyleText = try {
            llm.complete(
                systemPrompt = PromptTemplates.analysisAgent("plot-style-analyzer").systemPrompt,
                userMessage = chapters.joinToString("\n\n---\n\n") { it.content }.take(30000),
                temperature = PromptTemplates.analysisAgent("plot-style-analyzer").temperature,
                maxTokens = PromptTemplates.analysisAgent("plot-style-analyzer").maxTokens
            )
        } catch (e: Exception) {
            session.update { it.copy(phase = AnalysisPhase.FAILED, error = e.message) }
            emit(AnalysisEvent.Error(e.message ?: "情节与手法分析失败"))
            return@flow
        }
        val (plotSummary, styleProfile) = splitPlotAndStyle(plotStyleText)
        persistence.savePlotAndStyle(request.novelId, plotSummary, styleProfile)
        emit(AnalysisEvent.PhaseFinished(AnalysisPhase.PLOT_STYLE, plotStyleText))

        session.update { it.copy(phase = AnalysisPhase.COMPLETED, message = "解析完成") }
        emit(
            AnalysisEvent.Completed(
                "解析完成：共 ${chapters.size} 章，人物/世界观/梗概/手法画像已生成。"
            )
        )
    }

    private fun splitPlotAndStyle(text: String): Pair<String, String> {
        val plot = sectionAfterHeading(text, "情节梗概", "手法画像")
        val style = sectionAfterHeading(text, "手法画像", null)
        return (plot ?: "").trim() to (style ?: "").trim()
    }

    /**
     * 按 Markdown 标题定位章节内容：返回 [keyword] 所在标题行之后、
     * 下一个 [nextKeyword] 标题行之前（nextKeyword 为 null 时到文本末尾）的内容。
     * 标题行采用正则匹配，容忍标题变体（### 手法画像、【手法画像】等）。
     */
    private fun sectionAfterHeading(text: String, keyword: String, nextKeyword: String?): String? {
        val lines = text.lineSequence().toList()
        val startIdx = lines.indexOfFirst { line ->
            line.trim().matches(Regex("""#{1,6}\s*[【\[]?.*$keyword.*"""))
        }
        if (startIdx < 0) return null
        val endIdx = if (nextKeyword == null) {
            lines.size
        } else {
            val idx = lines.indexOfFirst { line ->
                line.trim().matches(Regex("""#{1,6}\s*[【\[]?.*$nextKeyword.*"""))
            }
            if (idx > startIdx) idx else lines.size
        }
        return lines.subList(startIdx + 1, endIdx).joinToString("\n")
    }
}
