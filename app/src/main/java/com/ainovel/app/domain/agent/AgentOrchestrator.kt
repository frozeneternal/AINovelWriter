package com.ainovel.app.domain.agent

import com.ainovel.app.domain.model.CreationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

data class ChapterResult(
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val continuityIssues: List<String> = emptyList()
)

data class PipelineRequest(
    val novelId: Long,
    val novelTitle: String,
    val genre: String,
    val theme: String,
    val style: String,
    val totalChapters: Int,
    val mode: CreationMode,
    val startChapterIndex: Int = 1,
    val styleProfile: String? = null,
    val plotSummary: String? = null,
    val skipSetup: Boolean = false,
    val existingWorldview: String = "",
    val existingChapters: List<PreviousChapter> = emptyList()
)

data class PipelineResult(
    val novelTitle: String,
    val worldview: String,
    val outline: String,
    val chapters: List<ChapterResult>,
    val summary: String
)

class AgentOrchestrator(
    private val llm: LlmGateway,
    private val contextManager: ContextManager
) {

    fun run(
        request: PipelineRequest,
        session: CreationSession
    ): Flow<PipelineEvent> = flow {
        suspend fun FlowCollector<PipelineEvent>.update(
            session: CreationSession,
            transform: (PipelineState) -> PipelineState
        ) {
            session.update(transform)
            emit(PipelineEvent.StateChanged(session.state.value))
        }

        session.markActive()

        val worldview: String
        val outline: String
        if (request.skipSetup) {
            worldview = request.existingWorldview
            outline = request.plotSummary.orEmpty()
            update(session) { it.copy(phase = PipelinePhase.WRITE_CHAPTER, message = "按原作手法续写中…") }
        } else {
            update(session) { it.copy(phase = PipelinePhase.WORLDVIEW, message = "世界观架构师构建设定中…") }
            emit(PipelineEvent.AgentStarted(PromptTemplates.agent("worldview-architect")))

            worldview = try {
                llm.complete(
                    systemPrompt = PromptTemplates.agent("worldview-architect").systemPrompt,
                    userMessage = PromptTemplates.buildNovelRequest(
                        title = request.novelTitle,
                        genre = request.genre,
                        theme = request.theme,
                        chapterCount = request.totalChapters,
                        style = request.style
                    ).content,
                    temperature = PromptTemplates.agent("worldview-architect").temperature,
                    maxTokens = PromptTemplates.agent("worldview-architect").maxTokens
                )
            } catch (e: Exception) {
                update(session) { it.copy(phase = PipelinePhase.FAILED, error = e.message) }
                emit(PipelineEvent.Error(e.message ?: "世界观生成失败"))
                return@flow
            }
            emit(PipelineEvent.AgentFinished(PromptTemplates.agent("worldview-architect"), worldview))

            if (request.mode == CreationMode.CONFIRM_STEP) {
                update(session) { it.copy(waitingConfirm = true, phase = PipelinePhase.WORLDVIEW) }
                emit(PipelineEvent.StepConfirmRequested(PromptTemplates.agent("worldview-architect"), worldview))
                while (session.state.value.waitingConfirm) {
                    kotlinx.coroutines.delay(200)
                }
            }

            update(session) { it.copy(phase = PipelinePhase.OUTLINE, message = "大纲规划师规划结构…") }
            emit(PipelineEvent.AgentStarted(PromptTemplates.agent("outline-planner")))

            outline = try {
                llm.complete(
                    systemPrompt = PromptTemplates.agent("outline-planner").systemPrompt,
                    userMessage = "书名：《${request.novelTitle}》\n题材：${request.genre}\n预计章节数：${request.totalChapters}\n\n【世界观设定】\n${worldview.take(6000)}",
                    temperature = PromptTemplates.agent("outline-planner").temperature,
                    maxTokens = PromptTemplates.agent("outline-planner").maxTokens
                )
            } catch (e: Exception) {
                update(session) { it.copy(phase = PipelinePhase.FAILED, error = e.message) }
                emit(PipelineEvent.Error(e.message ?: "大纲生成失败"))
                return@flow
            }
            emit(PipelineEvent.AgentFinished(PromptTemplates.agent("outline-planner"), outline))

            if (request.mode == CreationMode.CONFIRM_STEP) {
                update(session) { it.copy(waitingConfirm = true, phase = PipelinePhase.OUTLINE) }
                emit(PipelineEvent.StepConfirmRequested(PromptTemplates.agent("outline-planner"), outline))
                while (session.state.value.waitingConfirm) {
                    kotlinx.coroutines.delay(200)
                }
            }
        }

        val chapters = mutableListOf<ChapterResult>()
        for (i in request.startChapterIndex..request.totalChapters) {
            if (!session.isActive) break

            val chapterTitle = extractChapterTitle(outline, i)
            val previous = request.existingChapters + chapters.map { PreviousChapter(it.title, it.content) }

            update(session) {
                it.copy(
                    phase = PipelinePhase.WRITE_CHAPTER,
                    chapterIndex = i,
                    totalChapters = request.totalChapters,
                    streamingText = "",
                    message = "章节作者创作第 $i 章…"
                )
            }
            emit(PipelineEvent.AgentStarted(PromptTemplates.agent("chapter-author")))

            val context = contextManager.buildChapterContext(
                novelTitle = request.novelTitle,
                worldview = worldview,
                outline = outline,
                previousChapters = previous,
                chapterTitle = chapterTitle,
                plotSummary = request.plotSummary.orEmpty(),
                styleProfile = request.styleProfile.orEmpty()
            )

            val rawChapter = try {
                val sb = StringBuilder()
                val systemPrompt = buildString {
                    append(PromptTemplates.agent("chapter-author").systemPrompt)
                    if (!request.styleProfile.isNullOrBlank()) {
                        append("\n\n【续写模式】")
                        append("\n这是续写已有小说的场景。你必须严格模仿原作者的写作手法画像，")
                        append("保持叙事视角、句式节奏、描写密度、对话风格、悬念手法与全书一致。")
                        append("开篇与上一章结尾自然衔接，不得突兀改变风格。")
                    }
                }
                llm.streamChat(
                    systemPrompt = systemPrompt,
                    userMessage = context.toUserPrompt(),
                    temperature = PromptTemplates.agent("chapter-author").temperature,
                    maxTokens = PromptTemplates.agent("chapter-author").maxTokens
                ).collect { chunk ->
                    sb.append(chunk)
                    session.update { it.copy(streamingText = sb.toString()) }
                    emit(PipelineEvent.Token(chunk))
                }
                sb.toString()
            } catch (e: Exception) {
                update(session) { it.copy(phase = PipelinePhase.FAILED, error = e.message) }
                emit(PipelineEvent.Error(e.message ?: "第 $i 章生成失败"))
                return@flow
            }
            session.update { it.copy(streamingText = "") }
            emit(PipelineEvent.AgentFinished(PromptTemplates.agent("chapter-author"), rawChapter))

            // 连续性校验
            update(session) {
                it.copy(
                    phase = PipelinePhase.CONTINUITY_CHECK,
                    message = "连续性编辑校验第 $i 章…"
                )
            }
            emit(PipelineEvent.AgentStarted(PromptTemplates.agent("continuity-editor")))

            val (issues, corrected) = try {
                val verified = llm.complete(
                    systemPrompt = PromptTemplates.agent("continuity-editor").systemPrompt,
                    userMessage = buildString {
                        append("【世界观设定】\n").append(worldview.take(5000))
                        append("\n\n【大纲】\n").append(outline.take(2000))
                        val recentPrevious = previous.takeLast(3)
                        if (recentPrevious.isNotEmpty()) {
                            append("\n\n【前文（最近章节结尾）】\n")
                            append(recentPrevious.joinToString("\n\n---\n\n") { c ->
                                c.content.takeLast(800)
                            })
                        }
                        if (!request.styleProfile.isNullOrBlank()) {
                            append("\n\n【原作写作手法画像】\n")
                            append("校验情节与人物是否与前文及设定一致时，同时确认本章文风符合作者手法画像：\n")
                            append(request.styleProfile)
                        }
                        append("\n\n【本章正文】\n").append(rawChapter)
                    },
                    temperature = PromptTemplates.agent("continuity-editor").temperature,
                    maxTokens = PromptTemplates.agent("continuity-editor").maxTokens
                )
                parseContinuityOutput(verified, rawChapter)
            } catch (e: Exception) {
                emptyList<String>() to rawChapter
            }
            emit(PipelineEvent.ContinuityResult(issues, corrected))

            // 润色
            update(session) { it.copy(phase = PipelinePhase.POLISH, message = "润色编辑润色第 $i 章…") }
            emit(PipelineEvent.AgentStarted(PromptTemplates.agent("polish-editor")))

            val finalChapter = try {
                llm.complete(
                    systemPrompt = PromptTemplates.agent("polish-editor").systemPrompt,
                    userMessage = buildString {
                        if (!request.styleProfile.isNullOrBlank()) {
                            append("润色时必须严格保持以下原作者写作手法画像，不得改造成另一种风格：\n")
                            append(request.styleProfile)
                            append("\n\n")
                        }
                        append(corrected)
                    },
                    temperature = PromptTemplates.agent("polish-editor").temperature,
                    maxTokens = PromptTemplates.agent("polish-editor").maxTokens
                )
            } catch (e: Exception) {
                corrected
            }

            val title = extractChapterTitle(finalChapter, i)
                .ifBlank { "第 $i 章" }
            val content = stripChapterTitle(finalChapter)

            val result = ChapterResult(
                chapterIndex = i,
                title = title,
                content = content,
                continuityIssues = issues
            )
            chapters += result
            update(session) { it.copy(chapterIndex = i) }
            emit(PipelineEvent.ChapterGenerated(i, title, content))

            if (request.mode == CreationMode.CONFIRM_STEP) {
                update(session) { it.copy(waitingConfirm = true, phase = PipelinePhase.WRITE_CHAPTER) }
                emit(PipelineEvent.StepConfirmRequested(PromptTemplates.agent("chapter-author"), finalChapter))
                while (session.state.value.waitingConfirm) {
                    kotlinx.coroutines.delay(200)
                }
            }
        }

        update(session) { it.copy(phase = PipelinePhase.COMPLETED, message = "创作完成") }
        emit(
            PipelineEvent.Completed(
                "《${request.novelTitle}》共 ${chapters.size} 章创作完成。"
            )
        )
    }

    private fun extractChapterTitle(outline: String, index: Int): String {
        val marker = "第 $index 章"
        val idx = outline.indexOf(marker)
        if (idx < 0) return ""
        val rest = outline.substring(idx + marker.length)
        val end = rest.indexOfFirst { it == '\n' }
        val segment = if (end >= 0) rest.substring(0, end) else rest
        val cleaned = segment.trim().trimStart('《').trimEnd('》')
        return cleaned.ifBlank { "" }
    }

    private fun stripChapterTitle(chapter: String): String {
        val lines = chapter.lines()
        if (lines.isEmpty()) return chapter
        val first = lines.first().trim()
        if (first.startsWith("第") && (first.contains("章") || first.contains("回"))) {
            return lines.drop(1).joinToString("\n").trim()
        }
        return chapter.trim()
    }

    private fun parseContinuityOutput(output: String, fallback: String): Pair<List<String>, String> {
        val issues = mutableListOf<String>()
        val reportIdx = output.indexOf("## 一致性报告")
        val correctedIdx = output.indexOf("## 修正后章节")

        if (reportIdx >= 0 && correctedIdx > reportIdx) {
            val reportSection = output.substring(reportIdx, correctedIdx)
            reportSection.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.startsWith("- ") && !t.contains("无设定冲突")) {
                    issues += t.removePrefix("- ")
                }
            }
            val corrected = output.substring(correctedIdx + "## 修正后章节".length).trim()
            return issues to corrected.ifBlank { fallback }
        }
        return issues to output.trim()
    }
}
