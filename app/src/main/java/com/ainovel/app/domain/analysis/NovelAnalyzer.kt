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

    private companion object {
        /** 每个分析批次的字符预算：长篇小说全文分片分批送入 LLM，避免整本截断丢内容 */
        const val BATCH_CHAR_BUDGET = 8000
        /** 人物/世界观提取时单次送入的最大字符数 */
        const val SINGLE_CALL_MAX_CHARS = 16000
        /** 每批分析抽取的章节数上限（人物/世界观提取用） */
        const val PER_BATCH_CHAPTERS = 8
    }

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

        // 人物提取：长文按批抽取，每批独立提取后合并，避免只取前 20 章导致后文人物丢失
        session.update { it.copy(phase = AnalysisPhase.CHARACTERS, message = "人物提取中…") }
        emit(AnalysisEvent.PhaseStarted(AnalysisPhase.CHARACTERS))
        val charactersText = try {
            val batches = splitBatches(chapters, PER_BATCH_CHAPTERS, BATCH_CHAR_BUDGET)
            val outputs = batches.map { batch ->
                llm.complete(
                    systemPrompt = PromptTemplates.analysisAgent("character-extractor").systemPrompt,
                    userMessage = batch.joinToString("\n\n---\n\n") { it.content }.take(SINGLE_CALL_MAX_CHARS),
                    temperature = PromptTemplates.analysisAgent("character-extractor").temperature,
                    maxTokens = PromptTemplates.analysisAgent("character-extractor").maxTokens
                )
            }
            mergeCharacterOutputs(outputs)
        } catch (e: Exception) {
            session.update { it.copy(phase = AnalysisPhase.FAILED, error = e.message) }
            emit(AnalysisEvent.Error(e.message ?: "人物提取失败"))
            return@flow
        }
        persistence.saveCharacters(request.novelId, charactersText)
        emit(AnalysisEvent.PhaseFinished(AnalysisPhase.CHARACTERS, charactersText))

        // 世界观提取：长文分片分批提取，结构化合并各小节
        session.update { it.copy(phase = AnalysisPhase.WORLDVIEW, message = "世界观提取中…") }
        emit(AnalysisEvent.PhaseStarted(AnalysisPhase.WORLDVIEW))
        val worldviewText = try {
            val batches = splitBatches(chapters, PER_BATCH_CHAPTERS, BATCH_CHAR_BUDGET)
            val outputs = batches.map { batch ->
                llm.complete(
                    systemPrompt = PromptTemplates.analysisAgent("worldview-extractor").systemPrompt,
                    userMessage = batch.joinToString("\n\n---\n\n") { it.content }.take(SINGLE_CALL_MAX_CHARS),
                    temperature = PromptTemplates.analysisAgent("worldview-extractor").temperature,
                    maxTokens = PromptTemplates.analysisAgent("worldview-extractor").maxTokens
                )
            }
            mergeWorldviewOutputs(outputs)
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
            val batches = splitBatches(chapters, PER_BATCH_CHAPTERS, BATCH_CHAR_BUDGET)
            val outputs = batches.map { batch ->
                llm.complete(
                    systemPrompt = PromptTemplates.analysisAgent("plot-style-analyzer").systemPrompt,
                    userMessage = batch.joinToString("\n\n---\n\n") { it.content }.take(SINGLE_CALL_MAX_CHARS),
                    temperature = PromptTemplates.analysisAgent("plot-style-analyzer").temperature,
                    maxTokens = PromptTemplates.analysisAgent("plot-style-analyzer").maxTokens
                )
            }
            mergePlotStyleOutputs(outputs)
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

    /** 按章节数与字符预算分批，保证每批不会超长 */
    private fun splitBatches(
        chapters: List<SplitChapter>,
        maxChaptersPerBatch: Int,
        maxCharsPerBatch: Int
    ): List<List<SplitChapter>> {
        val batches = mutableListOf<MutableList<SplitChapter>>()
        var current = mutableListOf<SplitChapter>()
        var currentChars = 0
        for (ch in chapters) {
            if (current.isNotEmpty() && (current.size >= maxChaptersPerBatch || currentChars + ch.content.length > maxCharsPerBatch)) {
                batches += current
                current = mutableListOf()
                currentChars = 0
            }
            current += ch
            currentChars += ch.content.length
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    /**
     * 合并多批人物提取结果：保留各批的 "## 人物设定" 主体，
     * 按 "### " 人物小节标题去重（同名人保留第一处出现的描述）。
     */
    private fun mergeCharacterOutputs(outputs: List<String>): String {
        if (outputs.size <= 1) return outputs.firstOrNull().orEmpty()
        val seen = linkedSetOf<String>()
        val sections = mutableListOf<String>()
        outputs.forEach { out ->
            out.split(Regex("(?=### )")).forEach { block ->
                val title = block.lineSequence()
                    .firstOrNull { it.trimStart().startsWith("### ") }
                    ?.trim()
                if (title != null && seen.add(title)) {
                    sections += block.trim()
                }
            }
        }
        return if (sections.isEmpty()) {
            outputs.joinToString("\n\n---\n\n")
        } else {
            "## 人物设定\n\n" + sections.joinToString("\n\n")
        }
    }

    /**
     * 合并多批世界观提取结果：按 "## 地理设定 / ## 规则体系 / ## 时间线"
     * 小节分别拼接各批内容，确保全书世界观（不限于前几章）都被保留。
     */
    private fun mergeWorldviewOutputs(outputs: List<String>): String {
        if (outputs.size <= 1) return outputs.firstOrNull().orEmpty()
        val sectionKeys = listOf("地理设定", "规则体系", "时间线")
        val merged = sectionKeys.mapNotNull { key ->
            val parts = outputs.mapNotNull { extractSection(it, key) }
            if (parts.isEmpty()) null else "## $key\n" + parts.joinToString("\n\n")
        }
        return merged.joinToString("\n\n")
    }

    /**
     * 合并多批情节/手法分析：拼接各批的情节梗概与手法画像部分。
     */
    private fun mergePlotStyleOutputs(outputs: List<String>): String {
        if (outputs.size <= 1) return outputs.firstOrNull().orEmpty()
        val plots = mutableListOf<String>()
        val styles = mutableListOf<String>()
        outputs.forEach { out ->
            splitPlotAndStyle(out).let { (plot, style) ->
                if (plot.isNotBlank()) plots += plot
                if (style.isNotBlank()) styles += style
            }
        }
        return buildString {
            if (plots.isNotEmpty()) {
                append("## 情节梗概\n")
                append(plots.joinToString("\n\n"))
                append("\n\n")
            }
            if (styles.isNotEmpty()) {
                append("## 手法画像\n")
                append(styles.joinToString("\n\n"))
            }
        }.trim()
    }

    /** 提取文本中 [keyword] 标题之后、下一个 "## " 标题之前的段落内容 */
    private fun extractSection(text: String, keyword: String): String? {
        val lines = text.lineSequence().toList()
        val startIdx = lines.indexOfFirst { line ->
            line.trim().matches(Regex("""#{1,6}\s*[【\[]?.*$keyword.*"""))
        }
        if (startIdx < 0) return null
        var endIdx = lines.size
        for (i in startIdx + 1 until lines.size) {
            if (lines[i].trim().startsWith("## ")) {
                endIdx = i
                break
            }
        }
        return lines.subList(startIdx + 1, endIdx).joinToString("\n").trim().ifBlank { null }
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
