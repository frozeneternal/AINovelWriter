package com.ainovel.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.local.AppDatabase
import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.remote.MediaClient
import com.ainovel.app.data.repository.AssetRepository
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.model.ChapterStatus
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus
import com.ainovel.app.ui.reading.ReaderViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: NovelDao
    private lateinit var repository: NovelRepository
    private lateinit var viewModel: ReaderViewModel

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
        repository = NovelRepository(dao)
        val assetRepository = AssetRepository(
            context,
            repository,
            MediaClient(
                OkHttpClient(),
                com.ainovel.app.domain.model.ConfigProvider { throw AssertionError("测试不应触发网络") }
            )
        )
        viewModel = ReaderViewModel(repository, assetRepository)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.coroutineContext.cancelChildren()
        db.close()
    }

    private fun seedNovelWithChapters(chapterCount: Int): Long = runBlocking {
        val now = System.currentTimeMillis()
        val novelId = dao.insertNovel(
            NovelEntity(
                title = "测试书",
                synopsis = "简介",
                genre = "玄幻",
                status = NovelStatus.COMPLETED,
                currentChapterIndex = chapterCount,
                totalChapters = chapterCount,
                source = NovelSource.ORIGINAL,
                createdAt = now,
                updatedAt = now
            )
        )
        for (i in 1..chapterCount) {
            dao.insertChapter(
                ChapterEntity(
                    novelId = novelId,
                    indexInNovel = i,
                    title = "第 $i 章",
                    content = "第 $i 章正文内容，足够长的一段文字，用于模拟真实章节阅读。",
                    status = ChapterStatus.FINAL
                )
            )
        }
        // 预热 Room flow 查询链路，确保后续 viewModel 的 collect 能发射数据
        dao.observeChapters(novelId).first()
        novelId
    }

    private fun getChapters(novelId: Long): List<ChapterEntity> = runBlocking {
        dao.getChapters(novelId)
    }

    /** 轮询等待条件满足：推进主线程 Looper 与 Main TestDispatcher，让 Room Flow 发射并让 collect 生效 */
    private fun waitUntil(predicate: () -> Boolean) {
        var attempts = 0
        while (!predicate() && attempts < 500) {
            Thread.sleep(20)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            idleMainLooper()
            attempts++
        }
        assertThat(predicate()).isTrue()
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun init_loadsChapters_setsCurrentIndex() {
        val novelId = seedNovelWithChapters(5)
        viewModel.init(novelId, startIndex = 2)

        waitUntil { viewModel.uiState.value.chapters.size == 5 }

        assertThat(viewModel.uiState.value.chapters).hasSize(5)
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(2)
        assertThat(viewModel.uiState.value.chapters[2].title).isEqualTo("第 3 章")
    }

    @Test
    fun init_startIndexOutOfBounds_clampsToLastChapter() {
        val novelId = seedNovelWithChapters(3)
        viewModel.init(novelId, startIndex = 99)

        waitUntil { viewModel.uiState.value.chapters.size == 3 }

        // 超出章节数的起始索引应钳制到最后一章，而不是越界
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(2)
    }

    @Test
    fun navigateTo_nextAndPrevious_movesWithinRange() {
        val novelId = seedNovelWithChapters(4)
        viewModel.init(novelId, startIndex = 0)
        waitUntil { viewModel.uiState.value.chapters.size == 4 }

        viewModel.navigateTo(1)
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(1)
        viewModel.navigateTo(2)
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(2)
        viewModel.navigateTo(1)
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(1)
        // 翻章会退出编辑态
        viewModel.startEdit()
        assertThat(viewModel.uiState.value.editing).isTrue()
        viewModel.navigateTo(2)
        assertThat(viewModel.uiState.value.editing).isFalse()
    }

    @Test
    fun navigateTo_outOfBounds_clamps() {
        val novelId = seedNovelWithChapters(3)
        viewModel.init(novelId, startIndex = 1)
        waitUntil { viewModel.uiState.value.chapters.size == 3 }

        viewModel.navigateTo(100)
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(2)
        viewModel.navigateTo(-5)
        assertThat(viewModel.uiState.value.currentIndex).isEqualTo(0)
    }

    @Test
    fun edit_save_persistsDraftAndShowsSnackbar() {
        val novelId = seedNovelWithChapters(2)
        viewModel.init(novelId, startIndex = 0)
        waitUntil { viewModel.uiState.value.chapters.size == 2 }

        viewModel.startEdit()
        assertThat(viewModel.uiState.value.editing).isTrue()
        assertThat(viewModel.uiState.value.draftContent)
            .isEqualTo("第 1 章正文内容，足够长的一段文字，用于模拟真实章节阅读。")

        viewModel.updateDraft("修改后的第一章节正文")
        assertThat(viewModel.uiState.value.draftContent).isEqualTo("修改后的第一章节正文")

        viewModel.saveEdit()
        // saveEdit 是异步，等待编辑态退出（Room 事务在后台 executor 完成）
        waitUntil { !viewModel.uiState.value.editing }
        assertThat(viewModel.uiState.value.editing).isFalse()
        assertThat(viewModel.uiState.value.snackbar).isEqualTo("修改已保存")

        val saved = getChapters(novelId).first { it.indexInNovel == 1 }
        assertThat(saved.content).isEqualTo("修改后的第一章节正文")
    }

    @Test
    fun cancelEdit_discardsDraft() {
        val novelId = seedNovelWithChapters(2)
        viewModel.init(novelId, startIndex = 0)
        waitUntil { viewModel.uiState.value.chapters.size == 2 }

        viewModel.startEdit()
        viewModel.updateDraft("临时改动")
        viewModel.cancelEdit()

        assertThat(viewModel.uiState.value.editing).isFalse()
        // 取消编辑不落库，原文保持
        val chapter = getChapters(novelId).first { it.indexInNovel == 1 }
        assertThat(chapter.content).contains("第 1 章正文")
        assertThat(chapter.content).doesNotContain("临时改动")
    }
}
