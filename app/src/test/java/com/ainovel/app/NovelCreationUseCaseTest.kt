package com.ainovel.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ainovel.app.data.local.AppDatabase
import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.local.entity.WorldviewEntity
import com.ainovel.app.data.repository.HistoryRepository
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.agent.AgentOrchestrator
import com.ainovel.app.domain.agent.ContextManager
import com.ainovel.app.domain.agent.CreationSession
import com.ainovel.app.domain.agent.PipelineEvent
import com.ainovel.app.domain.agent.PipelinePhase
import com.ainovel.app.domain.agent.SummaryCompressor
import com.ainovel.app.domain.model.CreationMode
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus
import com.ainovel.app.domain.usecase.NovelCreationUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovelCreationUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NovelDao
    private lateinit var novelRepository: NovelRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var useCase: NovelCreationUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .allowMainThreadQueries()
            .build()
        dao = db.novelDao()
        novelRepository = NovelRepository(dao)
        historyRepository = HistoryRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedImportedNovel(totalChapters: Int = 10): Long = runBlocking {
        val now = System.currentTimeMillis()
        val novelId = dao.insertNovel(
            NovelEntity(
                title = "导入书",
                synopsis = "梗概",
                genre = "导入",
                status = NovelStatus.COMPLETED,
                currentChapterIndex = totalChapters,
                totalChapters = totalChapters,
                source = NovelSource.IMPORTED,
                createdAt = now,
                updatedAt = now
            )
        )
        for (i in 1..totalChapters) {
            dao.insertChapter(
                ChapterEntity(
                    novelId = novelId,
                    indexInNovel = i,
                    title = "第 $i 章",
                    content = "第 $i 章内容".repeat(50),
                    status = com.ainovel.app.domain.model.ChapterStatus.FINAL
                )
            )
        }
        dao.upsertWorldview(
            WorldviewEntity(
                novelId = novelId,
                characters = "主角：阿杰",
                plotSummary = "主线：少年成长",
                styleProfile = "叙事视角：第三人称限知；句式节奏：长短句交错；描写密度：动作与对话为主"
            )
        )
        novelId
    }

    @Test
    fun runContinuation_updatesTotalChaptersAndWritesNewChapters() = runBlocking {
        val novelId = seedImportedNovel(10)
        val fake = FakeLlmGateway()
        val recordedPrompts = mutableListOf<String>()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            if (systemPrompt.contains("章节作者")) {
                recordedPrompts += userMessage
                "第 11 章 新篇章\n续写正文内容".repeat(30)
            } else if (systemPrompt.contains("连续性编辑")) {
                "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 11 章 新篇章\n修正后的续写正文".repeat(20)
            } else if (systemPrompt.contains("润色编辑")) {
                "第 11 章 新篇章\n润色后的续写正文".repeat(20)
            } else {
                "第 11 章 新篇章\n默认正文".repeat(20)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        val events = useCase.runContinuation(
            novelId = novelId,
            totalNewChapters = 5,
            mode = CreationMode.AUTO
        ).toList()

        val novel = dao.getNovel(novelId)
        assertThat(novel).isNotNull()
        assertThat(novel!!.totalChapters).isEqualTo(15)
        assertThat(novel.currentChapterIndex).isEqualTo(15)
        assertThat(novel.status).isEqualTo(NovelStatus.COMPLETED)

        val chapters = dao.getChapters(novelId)
        assertThat(chapters).hasSize(15)
        assertThat(chapters.map { it.indexInNovel }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

        val newChapters = chapters.filter { it.indexInNovel > 10 }
        assertThat(newChapters).hasSize(5)
        newChapters.forEach { c ->
            assertThat(c.title.isNotBlank()).isTrue()
            assertThat(c.content.length).isGreaterThan(100)
        }

        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        val states = events.filterIsInstance<PipelineEvent.StateChanged>().map { it.state.phase }
        assertThat(states.last()).isEqualTo(PipelinePhase.COMPLETED)
        assertThat(recordedPrompts).isNotEmpty()
    }

    @Test
    fun runContinuation_injectsStyleProfileIntoChapterPrompt() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        val chapterPrompts = mutableListOf<String>()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("章节作者") -> {
                    chapterPrompts += userMessage
                    "第 4 章 续写\n正文".repeat(30)
                }
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(20)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        useCase.runContinuation(
            novelId = novelId,
            totalNewChapters = 1,
            mode = CreationMode.AUTO
        ).toList()

        assertThat(chapterPrompts).isNotEmpty()
        assertThat(chapterPrompts.first()).contains("续写要求")
        assertThat(chapterPrompts.first()).contains("第三人称限知")
    }

    @Test
    fun runContinuation_injectsContinuationDirectionIntoChapterPrompt() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        val chapterPrompts = mutableListOf<String>()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("章节作者") -> {
                    chapterPrompts += userMessage
                    "第 4 章 续写\n正文".repeat(30)
                }
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(20)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        useCase.runContinuation(
            novelId = novelId,
            totalNewChapters = 1,
            mode = CreationMode.AUTO,
            continuationDirection = "主角解开身世之谜后向帝都进发，遇见新对手"
        ).toList()

        assertThat(chapterPrompts).isNotEmpty()
        assertThat(chapterPrompts.first()).contains("【剧情发展方向】")
        assertThat(chapterPrompts.first()).contains("主角解开身世之谜后向帝都进发，遇见新对手")
        assertThat(chapterPrompts.first()).contains("续写要求")
    }

    @Test
    fun runContinuation_withoutDirection_omitsDirectionSection() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        val chapterPrompts = mutableListOf<String>()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("章节作者") -> {
                    chapterPrompts += userMessage
                    "第 4 章 续写\n正文".repeat(30)
                }
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(20)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        useCase.runContinuation(
            novelId = novelId,
            totalNewChapters = 1,
            mode = CreationMode.AUTO
        ).toList()

        assertThat(chapterPrompts).isNotEmpty()
        assertThat(chapterPrompts.first()).doesNotContain("【剧情发展方向】")
    }

    @Test
    fun runContinuation_agentsInvoked_butSkipsWorldviewAndOutline() = runBlocking {
        val novelId = seedImportedNovel(2)
        val fake = FakeLlmGateway()
        val invokedAgents = mutableListOf<String>()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            invokedAgents += systemPrompt.take(30)
            when {
                systemPrompt.contains("章节作者") -> "第 3 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 3 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 3 章 续写\n润色正文".repeat(20)
                else -> "第 3 章 续写\n正文".repeat(20)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        val events = useCase.runContinuation(
            novelId = novelId,
            totalNewChapters = 1,
            mode = CreationMode.AUTO
        ).toList()

        val started = events.filterIsInstance<PipelineEvent.AgentStarted>().map { it.agent.id }
        assertThat(started).contains("chapter-author")
        assertThat(started).contains("continuity-editor")
        assertThat(started).contains("polish-editor")
        assertThat(started).doesNotContain("worldview-architect")
        assertThat(started).doesNotContain("outline-planner")
    }

    @Test
    fun startContinuationInBackground_runsInAppScope_andExposesEvents() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(20)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        val events = mutableListOf<PipelineEvent>()
        val eventsJob = launch {
            useCase.events(novelId).collect { event -> events += event }
        }
        delay(200)
        assertThat(useCase.isRunning(novelId)).isFalse()

        val started = useCase.startContinuationInBackground(novelId, totalNewChapters = 2)
        assertThat(started).isTrue()
        assertThat(useCase.isRunning(novelId)).isTrue()
        assertThat(useCase.observeRunning(novelId).value).isTrue()

        // 重复启动同一本书应被忽略
        assertThat(useCase.startContinuationInBackground(novelId, totalNewChapters = 2)).isFalse()

        // 等待后台管线完成
        kotlinx.coroutines.withTimeout(15000) {
            while (useCase.isRunning(novelId)) {
                delay(100)
            }
        }

        val novel = dao.getNovel(novelId)
        assertThat(novel!!.totalChapters).isEqualTo(5)
        assertThat(dao.getChapters(novelId)).hasSize(5)
        assertThat(useCase.currentState(novelId)?.phase).isEqualTo(PipelinePhase.COMPLETED)
        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        assertThat(useCase.observeRunning(novelId).value).isFalse()

        eventsJob.cancel()
    }

    @Test
    fun cancelBackground_stopsPipeline() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            Thread.sleep(2000)
            when {
                systemPrompt.contains("章节作者") -> "第 4 章 续写\n正文".repeat(30)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        assertThat(useCase.startContinuationInBackground(novelId, totalNewChapters = 1)).isTrue()
        assertThat(useCase.isRunning(novelId)).isTrue()

        useCase.cancel(novelId)
        assertThat(useCase.isRunning(novelId)).isFalse()
        assertThat(useCase.observeRunning(novelId).value).isFalse()
    }

    @Test
    fun startPipelineInBackground_completedNovel_doesNotRestart() = runBlocking {
        // 书已写完（10/10）且状态 COMPLETED，再次进入创作不应重新启动管线
        val novelId = seedImportedNovel(10)
        val fake = FakeLlmGateway()
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        val started = useCase.startPipelineInBackground(
            novelId = novelId,
            title = "导入书",
            genre = "导入",
            theme = "梗概",
            style = "爽文风",
            totalChapters = 10,
            startChapterIndex = 11
        )
        assertThat(started).isFalse()
        assertThat(useCase.isRunning(novelId)).isFalse()
        assertThat(dao.getChapters(novelId)).hasSize(10)
    }

    @Test
    fun continuationMode_flagTrackedPerNovel() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        // 未启动时标志应为 false
        assertThat(useCase.isContinuationMode(novelId)).isFalse()

        assertThat(useCase.startContinuationInBackground(novelId, totalNewChapters = 1)).isTrue()
        assertThat(useCase.isContinuationMode(novelId)).isTrue()

        kotlinx.coroutines.withTimeout(15000) {
            while (useCase.isRunning(novelId)) {
                delay(100)
            }
        }

        // 完成后标志保留，供再次进入时修正续写语义
        assertThat(useCase.isContinuationMode(novelId)).isTrue()
        assertThat(useCase.currentState(novelId)?.phase).isEqualTo(PipelinePhase.COMPLETED)
    }

    @Test
    fun pauseAndResume_continuationPipeline() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            Thread.sleep(300)
            when {
                systemPrompt.contains("章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        useCase.startContinuationInBackground(novelId, totalNewChapters = 2)
        delay(600)
        useCase.pause(novelId)
        assertThat(useCase.isPaused(novelId)).isTrue()

        // 等待管线在章节边界进入暂停状态
        kotlinx.coroutines.withTimeout(10000) {
            while (true) {
                val phase: PipelinePhase? = useCase.currentState(novelId)?.phase
                if (phase == PipelinePhase.PAUSED) break
                delay(100)
            }
        }
        assertThat(useCase.isRunning(novelId)).isTrue()
        // 暂停期间章节数不变（仍在原 3 章或边界挂起）
        val chaptersWhilePaused = dao.getChapters(novelId).size

        useCase.resume(novelId)
        assertThat(useCase.isPaused(novelId)).isFalse()

        kotlinx.coroutines.withTimeout(20000) {
            while (useCase.isRunning(novelId)) {
                delay(100)
            }
        }

        assertThat(useCase.currentState(novelId)?.phase).isEqualTo(PipelinePhase.COMPLETED)
        assertThat(dao.getChapters(novelId)).hasSize(5)
        assertThat(chaptersWhilePaused).isAtMost(5)
    }

    @Test
    fun pauseDuringStreamingHoldsAfterCurrentCall() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            Thread.sleep(300)
            when {
                systemPrompt.contains("章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        useCase.startContinuationInBackground(novelId, totalNewChapters = 2)
        // 等章节作者流式调用开始后立即暂停：当前调用跑完即应挂起，不再进入连续性/润色
        delay(350)
        useCase.pause(novelId)
        assertThat(useCase.isPaused(novelId)).isTrue()

        kotlinx.coroutines.withTimeout(10000) {
            while (true) {
                val phase: PipelinePhase? = useCase.currentState(novelId)?.phase
                if (phase == PipelinePhase.PAUSED) break
                delay(100)
            }
        }
        // 挂起时仍处于写第 4 章阶段（尚未进入连续性/润色），暂停期间章节数不变
        assertThat(dao.getChapters(novelId)).hasSize(3)
        val chaptersWhilePaused = dao.getChapters(novelId).size

        useCase.resume(novelId)
        assertThat(useCase.isPaused(novelId)).isFalse()

        kotlinx.coroutines.withTimeout(20000) {
            while (useCase.isRunning(novelId)) {
                delay(100)
            }
        }

        assertThat(useCase.currentState(novelId)?.phase).isEqualTo(PipelinePhase.COMPLETED)
        assertThat(dao.getChapters(novelId)).hasSize(5)
        assertThat(chaptersWhilePaused).isEqualTo(3)
    }

    @Test
    fun resumeBeforePipelineReachesPausePointStillCompletes() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            Thread.sleep(200)
            when {
                systemPrompt.contains("章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            historyRepository
        )

        useCase.startContinuationInBackground(novelId, totalNewChapters = 2)
        // 模拟用户快速操作：暂停后立刻恢复（管线可能尚未到达挂起点）
        delay(100)
        useCase.pause(novelId)
        assertThat(useCase.isPaused(novelId)).isTrue()
        delay(100)
        useCase.resume(novelId)
        assertThat(useCase.isPaused(novelId)).isFalse()
        // 关键断言：resume 立即把 phase 从 PAUSED 恢复为 WRITE_CHAPTER，
        // 否则 UI 停在"已暂停"按钮，用户误以为无法继续
        assertThat(useCase.currentState(novelId)?.phase).isEqualTo(PipelinePhase.WRITE_CHAPTER)

        kotlinx.coroutines.withTimeout(20000) {
            while (useCase.isRunning(novelId)) {
                delay(100)
            }
        }

        // 关键断言：无论暂停/恢复时序如何，管线最终都应完成全部章节
        assertThat(useCase.currentState(novelId)?.phase).isEqualTo(PipelinePhase.COMPLETED)
        assertThat(dao.getChapters(novelId)).hasSize(5)
    }
}
