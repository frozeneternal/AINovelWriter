package com.ainovel.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ainovel.app.data.local.AppDatabase
import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.model.NovelStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NovelLifecycleRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NovelDao
    private lateinit var repository: NovelRepository

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createNovel(title: String = "测试书籍"): Long {
        return dao.insertNovel(
            com.ainovel.app.data.local.entity.NovelEntity(
                title = title,
                synopsis = "简介",
                genre = "玄幻",
                source = com.ainovel.app.domain.model.NovelSource.ORIGINAL,
                status = NovelStatus.DRAFT
            )
        )
    }

    private suspend fun seedChapters(novelId: Long, count: Int) {
        repeat(count) { i ->
            dao.insertChapter(
                com.ainovel.app.data.local.entity.ChapterEntity(
                    novelId = novelId,
                    indexInNovel = i + 1,
                    title = "第 ${i + 1} 章",
                    content = "内容 ${i + 1}"
                )
            )
        }
    }

    @Test
    fun softDelete_movesNovelToDeletedList() = runBlocking {
        val id = createNovel()

        repository.softDeleteNovel(id)

        val active = repository.observeActiveNovels().first()
        val deleted = repository.observeDeletedNovels().first()
        assertThat(active.map { it.id }).doesNotContain(id)
        assertThat(deleted.map { it.id }).contains(id)
        assertThat(dao.getNovel(id)!!.deletedAt).isNotNull()
    }

    @Test
    fun restore_bringsNovelBackToActiveList() = runBlocking {
        val id = createNovel()
        repository.softDeleteNovel(id)
        repository.restoreNovel(id)

        val active = repository.observeActiveNovels().first()
        assertThat(active.map { it.id }).contains(id)
        assertThat(dao.getNovel(id)!!.deletedAt).isNull()
    }

    @Test
    fun purge_removesNovelAndRelatedRows() = runBlocking {
        val id = createNovel()
        seedChapters(id, 3)
        repository.importNovel("关联书.txt", "正文内容")

        repository.softDeleteNovel(id)
        repository.purgeNovel(id)

        assertThat(dao.getNovel(id)).isNull()
        assertThat(dao.observeChapters(id).first()).isEmpty()
    }

    @Test
    fun saveCreationPrompt_storesDirectionAndWordCount() = runBlocking {
        val id = createNovel()

        repository.saveCreationPrompt(id, "主角觉醒星火之力", 3000)

        val novel = dao.getNovel(id)!!
        assertThat(novel.lastDirection).isEqualTo("主角觉醒星火之力")
        assertThat(novel.lastChapterWordCount).isEqualTo(3000)
    }

    @Test
    fun saveCreationPrompt_isPerNovelIsolated() = runBlocking {
        val a = createNovel("书A")
        val b = createNovel("书B")

        repository.saveCreationPrompt(a, "方向A", 2000)

        val novelA = dao.getNovel(a)!!
        val novelB = dao.getNovel(b)!!
        assertThat(novelA.lastDirection).isEqualTo("方向A")
        assertThat(novelB.lastDirection).isEmpty()
        assertThat(novelB.lastChapterWordCount).isEqualTo(0)
    }

    @Test
    fun deleteChapterAndRenumber_renumbersRemainingChapters() = runBlocking {
        val id = createNovel()
        seedChapters(id, 3)

        val chapters = dao.observeChapters(id).first().sortedBy { it.indexInNovel }
        val removed = chapters.first { it.indexInNovel == 2 }

        repository.deleteChapterAndRenumber(removed.id)

        val remaining = dao.observeChapters(id).first().sortedBy { it.indexInNovel }
        assertThat(remaining).hasSize(2)
        assertThat(remaining.map { it.indexInNovel }).containsExactly(1, 2).inOrder()
        assertThat(remaining.map { it.title }).containsExactly("第 1 章", "第 3 章").inOrder()
    }

    @Test
    fun deleteChapterAndRenumber_updatesNovelCountsAndFallsBackToDraftWhenEmpty() = runBlocking {
        val id = createNovel()
        seedChapters(id, 1)
        dao.updateNovel(
            dao.getNovel(id)!!.copy(
                totalChapters = 1,
                currentChapterIndex = 1,
                status = NovelStatus.WRITING
            )
        )

        val only = dao.observeChapters(id).first().first()
        repository.deleteChapterAndRenumber(only.id)

        val novel = dao.getNovel(id)!!
        assertThat(dao.observeChapters(id).first()).isEmpty()
        assertThat(novel.totalChapters).isEqualTo(0)
        assertThat(novel.currentChapterIndex).isEqualTo(0)
        assertThat(novel.status).isEqualTo(NovelStatus.DRAFT)
    }

    @Test
    fun deleteChapterAndRenumber_clampsCurrentChapterIndex() = runBlocking {
        val id = createNovel()
        seedChapters(id, 3)
        dao.updateNovel(
            dao.getNovel(id)!!.copy(
                totalChapters = 3,
                currentChapterIndex = 3,
                status = NovelStatus.WRITING
            )
        )

        val last = dao.observeChapters(id).first().first { it.indexInNovel == 3 }
        repository.deleteChapterAndRenumber(last.id)

        val novel = dao.getNovel(id)!!
        assertThat(novel.totalChapters).isEqualTo(2)
        assertThat(novel.currentChapterIndex).isEqualTo(2)
    }
}
