package com.ainovel.app.domain.usecase

import com.ainovel.app.data.repository.HistoryRepository
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.agent.AgentOrchestrator
import com.ainovel.app.domain.agent.CreationSession
import com.ainovel.app.domain.agent.PipelineEvent
import com.ainovel.app.domain.agent.PipelineRequest
import com.ainovel.app.domain.agent.PipelineState
import com.ainovel.app.domain.model.CreationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelCreationUseCase @Inject constructor(
    private val orchestrator: AgentOrchestrator,
    private val novelRepository: NovelRepository,
    private val historyRepository: HistoryRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = mutableMapOf<Long, CreationSession>()
    private val activeJobs = mutableMapOf<Long, Job>()
    private val eventFlows = mutableMapOf<Long, MutableSharedFlow<PipelineEvent>>()
    private val runningStates = mutableMapOf<Long, MutableStateFlow<Boolean>>()

    fun getSession(novelId: Long): CreationSession? = sessions[novelId]

    /**
     * 订阅某本书创作管线的实时事件。退出创作页后仍可重新订阅（后台管线持续运行）。
     */
    fun events(novelId: Long): SharedFlow<PipelineEvent> =
        eventFlows.getOrPut(novelId) { MutableSharedFlow(extraBufferCapacity = 128) }.asSharedFlow()

    /**
     * 当前管线状态快照，用于 UI 重新进入时恢复进度。
     */
    fun currentState(novelId: Long): PipelineState? = sessions[novelId]?.state?.value

    fun isRunning(novelId: Long): Boolean = activeJobs[novelId]?.isActive == true

    fun observeRunning(novelId: Long): StateFlow<Boolean> =
        runningStates.getOrPut(novelId) { MutableStateFlow(false) }.asStateFlow()

    private fun setRunning(novelId: Long, running: Boolean) {
        runningStates.getOrPut(novelId) { MutableStateFlow(false) }.value = running
    }

    /**
     * 在应用级后台作用域启动创作管线，不随任何 UI 生命周期取消。
     * 若该书已在创作中则忽略，返回 false。
     */
    fun startPipelineInBackground(
        novelId: Long,
        title: String,
        genre: String,
        theme: String,
        style: String,
        totalChapters: Int,
        startChapterIndex: Int = 1
    ): Boolean {
        if (isRunning(novelId)) return false
        val job = scope.launch {
            runPipeline(
                novelId = novelId,
                title = title,
                genre = genre,
                theme = theme,
                style = style,
                totalChapters = totalChapters,
                mode = CreationMode.AUTO,
                startChapterIndex = startChapterIndex
            ).collect { event ->
                eventFlows[novelId]?.tryEmit(event)
            }
        }
        registerJob(novelId, job)
        return true
    }

    /**
     * 在应用级后台作用域启动续写管线，不随任何 UI 生命周期取消。
     */
    fun startContinuationInBackground(
        novelId: Long,
        totalNewChapters: Int
    ): Boolean {
        if (isRunning(novelId)) return false
        val job = scope.launch {
            runContinuation(
                novelId = novelId,
                totalNewChapters = totalNewChapters,
                mode = CreationMode.AUTO
            ).collect { event ->
                eventFlows[novelId]?.tryEmit(event)
            }
        }
        registerJob(novelId, job)
        return true
    }

    private fun registerJob(novelId: Long, job: Job) {
        activeJobs[novelId] = job
        setRunning(novelId, true)
        job.invokeOnCompletion {
            activeJobs.remove(novelId)
            setRunning(novelId, false)
        }
    }

    private suspend fun markWriting(novelId: Long) {
        novelRepository.getNovel(novelId)?.let { n ->
            if (n.status != com.ainovel.app.domain.model.NovelStatus.WRITING) {
                novelRepository.updateNovel(
                    n.copy(status = com.ainovel.app.domain.model.NovelStatus.WRITING)
                )
            }
        }
    }

    fun runPipeline(
        novelId: Long,
        title: String,
        genre: String,
        theme: String,
        style: String,
        totalChapters: Int,
        mode: CreationMode,
        startChapterIndex: Int = 1
    ): Flow<PipelineEvent> {
        val session = CreationSession(novelId, mode)
        sessions[novelId] = session

        return orchestrator.run(
            request = PipelineRequest(
                novelId = novelId,
                novelTitle = title,
                genre = genre,
                theme = theme,
                style = style,
                totalChapters = totalChapters,
                mode = mode,
                startChapterIndex = startChapterIndex
            ),
            session = session
        ).onStart { markWriting(novelId) }.onEach { event ->
            when (event) {
                is PipelineEvent.ChapterGenerated -> {
                    novelRepository.saveChapter(
                        novelId = novelId,
                        index = event.chapterIndex,
                        title = event.title,
                        content = event.content,
                        summary = null
                    )
                    historyRepository.recordSuccess(
                        novelId = novelId,
                        agentRole = "chapter-author",
                        inputSummary = "第 ${event.chapterIndex} 章 ${event.title}",
                        outputText = event.content.take(2000)
                    )
                }
                is PipelineEvent.AgentFinished -> {
                    when (event.agent.id) {
                        "worldview-architect" -> {
                            novelRepository.upsertWorldview(novelId, event.output)
                            historyRepository.recordSuccess(
                                novelId, "worldview-architect",
                                "世界观设定", event.output.take(2000)
                            )
                        }
                        "outline-planner" -> {
                            novelRepository.upsertOutline(novelId, event.output)
                            historyRepository.recordSuccess(
                                novelId, "outline-planner",
                                "大纲规划", event.output.take(2000)
                            )
                        }
                    }
                }
                is PipelineEvent.Error -> {
                    historyRepository.recordFailure(
                        novelId = novelId,
                        agentRole = "pipeline",
                        inputSummary = "创作管线",
                        errorMessage = event.message
                    )
                }
                else -> Unit
            }
        }
    }

    fun confirmStep(novelId: Long) {
        sessions[novelId]?.update { it.copy(waitingConfirm = false) }
    }

    fun rejectStep(novelId: Long) {
        sessions[novelId]?.update { it.copy(waitingConfirm = false) }
    }

    fun cancel(novelId: Long) {
        activeJobs.remove(novelId)?.cancel()
        sessions[novelId]?.update { it.copy(waitingConfirm = false) }
        sessions[novelId]?.reset()
        sessions.remove(novelId)
        setRunning(novelId, false)
    }

    /**
     * 导入小说的续写入口：读取解析档案（人物/世界观/梗概/手法画像），
     * 从当前章节之后组装 PipelineRequest 走原创作管线。
     */
    suspend fun runContinuation(
        novelId: Long,
        totalNewChapters: Int,
        mode: CreationMode
    ): Flow<PipelineEvent> {
        val novel = novelRepository.getNovel(novelId) ?: error("书籍不存在")
        val worldview = novelRepository.getWorldview(novelId)
        val storedChapters = novelRepository.getChapters(novelId)
        val startIndex = storedChapters.size + 1

        val styleProfile = worldview?.styleProfile?.takeIf { it.isNotBlank() }
            ?: "叙事视角：第三人称限知视角；句式节奏：长短句交错，段落短促；描写密度：动作与对话为主，心理描写节制；对话风格：口语化、辨识度高；悬念手法：章末留钩子。"
        val plotSummary = worldview?.plotSummary?.takeIf { it.isNotBlank() } ?: ""
        val worldviewText = buildString {
            if (!worldview?.characters.isNullOrBlank()) append("## 人物设定\n${worldview?.characters}\n\n")
            if (!worldview?.geography.isNullOrBlank()) append("## 地理设定\n${worldview?.geography}\n\n")
            if (!worldview?.rules.isNullOrBlank()) append("## 规则体系\n${worldview?.rules}\n\n")
            if (!worldview?.timeline.isNullOrBlank()) append("## 时间线\n${worldview?.timeline}")
        }
        val previousChapters = storedChapters.map {
            com.ainovel.app.domain.agent.PreviousChapter(
                title = it.title.ifBlank { "第 ${it.indexInNovel} 章" },
                content = it.content
            )
        }

        val session = CreationSession(novelId, mode)
        sessions[novelId] = session

        markWriting(novelId)

        val targetTotalChapters = startIndex + totalNewChapters - 1
        novelRepository.getNovel(novelId)?.let { n ->
            novelRepository.updateNovel(n.copy(totalChapters = targetTotalChapters))
        }

        return orchestrator.run(
            request = PipelineRequest(
                novelId = novelId,
                novelTitle = novel.title,
                genre = "续写",
                theme = "",
                style = "严格模仿原作者手法",
                totalChapters = targetTotalChapters,
                mode = mode,
                startChapterIndex = startIndex,
                styleProfile = styleProfile,
                plotSummary = plotSummary,
                skipSetup = true,
                existingWorldview = worldviewText,
                existingChapters = previousChapters
            ),
            session = session
        ).onEach { event ->
            when (event) {
                is PipelineEvent.ChapterGenerated -> {
                    novelRepository.saveChapter(
                        novelId = novelId,
                        index = event.chapterIndex,
                        title = event.title,
                        content = event.content,
                        summary = null
                    )
                    historyRepository.recordSuccess(
                        novelId = novelId,
                        agentRole = "chapter-author",
                        inputSummary = "第 ${event.chapterIndex} 章 ${event.title}",
                        outputText = event.content.take(2000)
                    )
                }
                is PipelineEvent.Error -> {
                    historyRepository.recordFailure(
                        novelId = novelId,
                        agentRole = "pipeline",
                        inputSummary = "续写管线",
                        errorMessage = event.message
                    )
                }
                else -> Unit
            }
        }
    }
}
