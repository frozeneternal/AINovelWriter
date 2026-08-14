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
import com.ainovel.app.domain.agent.PipelinePhase
import com.ainovel.app.domain.agent.SummaryCompressor
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus
import com.ainovel.app.domain.usecase.NovelCreationUseCase
import com.ainovel.app.ui.creation.CreationRunViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreationRunViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NovelDao
    private lateinit var novelRepository: NovelRepository
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
        useCase = NovelCreationUseCase(
            AgentOrchestrator(FakeLlmGateway(), ContextManager(SummaryCompressor())),
            novelRepository,
            HistoryRepository(dao)
        )
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
    fun startAfterStop_resumeEnterKeepsStoppedState_withoutAutoRestart() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            Thread.sleep(200)
            when {
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            HistoryRepository(dao)
        )

        assertThat(useCase.startContinuationInBackground(novelId, totalNewChapters = 3)).isTrue()
        // 等管线进入运行中，再停止
        kotlinx.coroutines.withTimeout(10000) {
            while (!useCase.isRunning(novelId)) delay(50)
        }
        useCase.cancel(novelId)
        assertThat(useCase.isStopped(novelId)).isTrue()

        // 模拟用户停止后重新进入创作页（resume=true）：应保持"已停止生成"，不得自动重启管线
        val viewModel = CreationRunViewModel(novelRepository, useCase)
        viewModel.startIfNeeded(id = novelId, resume = true)
        assertThat(viewModel.state.value.phase).isEqualTo(PipelinePhase.CANCELLED)
        // 顶部标题必须同步切到"已停止生成"，避免停留在停止前的世界观/大纲阶段标签
        assertThat(viewModel.phaseLabel.value).isEqualTo("已停止生成")
        assertThat(useCase.isRunning(novelId)).isFalse()
        // 稍等片刻确认管线确实没有自动重启
        delay(300)
        assertThat(useCase.isRunning(novelId)).isFalse()
    }

    @Test
    fun startAfterStop_explicitStartClearsStopped_andRuns() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            Thread.sleep(200)
            when {
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 4 章 续写\n正文".repeat(30)
                systemPrompt.contains("连续性编辑") ->
                    "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 4 章 续写\n修正正文".repeat(20)
                systemPrompt.contains("润色编辑") -> "第 4 章 续写\n润色正文".repeat(20)
                else -> "第 4 章 续写\n正文".repeat(30)
            }
        }
        useCase = NovelCreationUseCase(
            AgentOrchestrator(fake, ContextManager(SummaryCompressor())),
            novelRepository,
            HistoryRepository(dao)
        )

        assertThat(useCase.startContinuationInBackground(novelId, totalNewChapters = 3)).isTrue()
        kotlinx.coroutines.withTimeout(10000) {
            while (!useCase.isRunning(novelId)) delay(50)
        }
        useCase.cancel(novelId)
        assertThat(useCase.isStopped(novelId)).isTrue()

        // 用户显式发起新的续写（resume=false）：应清除 stopped 标记并启动
        val viewModel = CreationRunViewModel(novelRepository, useCase)
        viewModel.startIfNeeded(id = novelId, isContinuation = true, resume = false, chapters = 2)
        assertThat(useCase.isStopped(novelId)).isFalse()
        assertThat(useCase.isRunning(novelId)).isTrue()
        assertThat(useCase.isContinuationMode(novelId)).isTrue()
    }
}
