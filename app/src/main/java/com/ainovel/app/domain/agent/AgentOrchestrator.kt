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
    val existingChapters: List<PreviousChapter> = emptyList(),
    val continuationDirection: String = ""
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

    private companion object {
        const val CONTENT_RETRY_MAX = 2
        const val CONTENT_COMPLIANCE_HINT =
            "\n\n【内容合规要求】\n" +
                "上轮请求因触发内容安全审核被拒绝。请调整上述创作要求：避免任何可能触发" +
                "违禁词或敏感内容审查的表述，改用含蓄、隐喻、间接的方式表达相同情节与人物，" +
                "保持剧情推进与人物塑造完整，但不得出现任何违规词句。"
    }

    /**
     * 对 LLM 调用做内容合规自动重试：捕获 [ContentPolicyException] 后，
     * 在用户消息末尾追加合规指令并重试，最多 [CONTENT_RETRY_MAX] 次。
     * 重试耗尽仍失败则抛出原异常，由调用方决定降级或失败。
     */
    private suspend fun <T> withContentComplianceRetry(
        systemPrompt: String,
        userMessage: String,
        block: suspend (systemPrompt: String, userMessage: String) -> T
    ): T {
        var attempts = 0
        var currentSystem = systemPrompt
        var currentUser = userMessage
        while (true) {
            try {
                return block(currentSystem, currentUser)
            } catch (e: ContentPolicyException) {
                attempts++
                if (attempts > CONTENT_RETRY_MAX) throw e
                currentUser = currentUser + CONTENT_COMPLIANCE_HINT
            }
        }
    }

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

        // 在每次 LLM 调用前检查暂停：若已暂停则进入 PAUSED 并挂起，恢复后继续
        suspend fun FlowCollector<PipelineEvent>.awaitResume(
            session: CreationSession,
            message: String
        ) {
            if (!session.state.value.paused) return
            update(session) {
                it.copy(
                    phase = PipelinePhase.PAUSED,
                    message = "已暂停，点击继续恢复生成",
                    currentAgent = null,
                    streamingText = ""
                )
            }
            while (session.state.value.paused && session.isActive) {
                kotlinx.coroutines.delay(200)
            }
            if (!session.isActive) return
            update(session) { it.copy(phase = PipelinePhase.WRITE_CHAPTER, message = message) }
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

            awaitResume(session, "已恢复，继续构建设定…")
            worldview = try {
                withContentComplianceRetry(
                    systemPrompt = PromptTemplates.agent("worldview-architect").systemPrompt,
                    userMessage = PromptTemplates.buildNovelRequest(
                        title = request.novelTitle,
                        genre = request.genre,
                        theme = request.theme,
                        chapterCount = request.totalChapters,
                        style = request.style
                    ).content
                ) { sys, user ->
                    llm.complete(
                        systemPrompt = sys,
                        userMessage = user,
                        temperature = PromptTemplates.agent("worldview-architect").temperature,
                        maxTokens = PromptTemplates.agent("worldview-architect").maxTokens
                    )
                }
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

            awaitResume(session, "已恢复，继续规划结构…")
            outline = try {
                withContentComplianceRetry(
                    systemPrompt = PromptTemplates.agent("outline-planner").systemPrompt,
                    userMessage = "书名：《${request.novelTitle}》\n题材：${request.genre}\n预计章节数：${request.totalChapters}\n\n【世界观设定】\n${worldview.take(6000)}"
                ) { sys, user ->
                    llm.complete(
                        systemPrompt = sys,
                        userMessage = user,
                        temperature = PromptTemplates.agent("outline-planner").temperature,
                        maxTokens = PromptTemplates.agent("outline-planner").maxTokens
                    )
                }
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
                styleProfile = request.styleProfile.orEmpty(),
                continuationDirection = request.continuationDirection
            )

            val rawChapter = try {
                val systemPrompt = buildString {
                    append(PromptTemplates.agent("chapter-author").systemPrompt)
                    if (!request.styleProfile.isNullOrBlank()) {
                        append("\n\n【续写模式】")
                        append("\n这是续写已有小说的场景。你必须严格模仿原作者的写作手法画像，")
                        append("保持叙事视角、句式节奏、描写密度、对话风格、悬念手法与全书一致。")
                        append("开篇与上一章结尾自然衔接，不得突兀改变风格。")
                    }
                }
                withContentComplianceRetry(
                    systemPrompt = systemPrompt,
                    userMessage = context.toUserPrompt()
                ) { sys, user ->
                    val sb = StringBuilder()
                    awaitResume(session, "已恢复，继续创作第 $i 章…")
                    llm.streamChat(
                        systemPrompt = sys,
                        userMessage = user,
                        temperature = PromptTemplates.agent("chapter-author").temperature,
                        maxTokens = PromptTemplates.agent("chapter-author").maxTokens
                    ).collect { chunk ->
                        sb.append(chunk)
                        session.update { it.copy(streamingText = sb.toString()) }
                        emit(PipelineEvent.Token(chunk))
                    }
                    sb.toString()
                }
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

            awaitResume(session, "已恢复，继续校验第 $i 章…")
            val (issues, corrected) = try {
                val verified = withContentComplianceRetry(
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
                    }
                ) { sys, user ->
                    llm.complete(
                        systemPrompt = sys,
                        userMessage = user,
                        temperature = PromptTemplates.agent("continuity-editor").temperature,
                        maxTokens = PromptTemplates.agent("continuity-editor").maxTokens
                    )
                }
                parseContinuityOutput(verified, rawChapter)
            } catch (e: Exception) {
                emptyList<String>() to rawChapter
            }
            emit(PipelineEvent.ContinuityResult(issues, corrected))

            // 润色
            update(session) { it.copy(phase = PipelinePhase.POLISH, message = "润色编辑润色第 $i 章…") }
            emit(PipelineEvent.AgentStarted(PromptTemplates.agent("polish-editor")))

            awaitResume(session, "已恢复，继续润色第 $i 章…")
            val finalChapter = try {
                withContentComplianceRetry(
                    systemPrompt = PromptTemplates.agent("polish-editor").systemPrompt,
                    userMessage = buildString {
                        if (!request.styleProfile.isNullOrBlank()) {
                            append("润色时必须严格保持以下原作者写作手法画像，不得改造成另一种风格：\n")
                            append(request.styleProfile)
                            append("\n\n")
                        }
                        append(corrected)
                    }
                ) { sys, user ->
                    llm.complete(
                        systemPrompt = sys,
                        userMessage = user,
                        temperature = PromptTemplates.agent("polish-editor").temperature,
                        maxTokens = PromptTemplates.agent("polish-editor").maxTokens
                    )
                }
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

        if (reportIdx >= 0) {
            val reportSection = if (correctedIdx > reportIdx) {
                output.substring(reportIdx, correctedIdx)
            } else {
                output.substring(reportIdx)
            }
            reportSection.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.startsWith("- ") && !t.contains("无设定冲突")) {
                    issues += t.removePrefix("- ")
                }
            }
        }
        // 只有发现问题时模型才输出修正后章节；未输出则保留原章节正文
        if (correctedIdx >= 0) {
            val corrected = output.substring(correctedIdx + "## 修正后章节".length).trim()
            if (corrected.isNotBlank()) return issues to corrected
        }
        return issues to fallback
    }
}
