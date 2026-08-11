package com.ainovel.app

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ainovel.app.data.local.AppDatabase
import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.ui.importing.ImportUiState
import com.ainovel.app.ui.importing.ImportViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImportFlowRobolectricTest {

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

    @Test
    fun importNovel_persistsNovelAndImportedText() = runBlocking {
        val id = repository.importNovel("test_novel.txt", "第一章\n\n内容一\n\n第二章\n\n内容二")

        assertThat(id).isGreaterThan(0L)
        val novel = dao.getNovel(id)
        assertThat(novel).isNotNull()
        assertThat(novel!!.title).isEqualTo("test_novel")
        assertThat(novel.source.name).isEqualTo("IMPORTED")
        assertThat(novel.status.name).isEqualTo("DRAFT")

        val text = dao.getImportedText(id)
        assertThat(text).isNotNull()
        assertThat(text!!.fullText).contains("第一章")
    }

    @Test
    fun importViewModel_importAndStart_flowEndsInImportedId() {
        val viewModel = ImportViewModel(repository)
        val uri = Uri.parse("content://test/novel.txt")
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, "第一章\n\n内容\n\n第二章\n\n结尾".byteInputStream())

        viewModel.loadFile(context, uri)
        idleMainLooper()

        val afterLoad = waitForState(viewModel) { !it.loading && it.fullText.isNotEmpty() }
        assertThat(afterLoad.error).isNull()
        assertThat(afterLoad.fullText).contains("第一章")
        assertThat(afterLoad.title).isEqualTo("novel")

        viewModel.importAndStart()
        idleMainLooper()

        val novel = waitForNovelInDb()
        assertThat(novel).isNotNull()
        assertThat(novel!!.title).isEqualTo("novel")
        assertThat(novel.source.name).isEqualTo("IMPORTED")
        assertThat(runBlocking { repository.getImportedText(novel.id) }).isNotNull()
    }
    private fun waitForNovelInDb(): com.ainovel.app.data.local.entity.NovelEntity? {
        var attempts = 0
        var novel: com.ainovel.app.data.local.entity.NovelEntity? = null
        while (attempts < 200) {
            android.os.SystemClock.sleep(20)
            idleMainLooper()
            runBlocking {
                val id = dao.observeNovels().first().lastOrNull()?.id
                novel = if (id != null) dao.getNovel(id) else null
            }
            if (novel != null && runBlocking { dao.getImportedText(novel.id) } != null) {
                return novel
            }
            attempts++
        }
        return novel
    }

    private fun waitForState(
        viewModel: ImportViewModel,
        predicate: (ImportUiState) -> Boolean
    ): ImportUiState {
        var state = viewModel.state.value
        var attempts = 0
        while (!predicate(state) && attempts < 200) {
            android.os.SystemClock.sleep(20)
            idleMainLooper()
            state = viewModel.state.value
            attempts++
        }
        return state
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun importViewModel_blankFile_showsError() {
        val viewModel = ImportViewModel(repository)
        val uri = Uri.parse("content://test/empty.txt")
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, "   ".byteInputStream())

        viewModel.loadFile(context, uri)
        idleMainLooper()

        val state = waitForState(viewModel) { !it.loading }
        assertThat(state.error).isNotNull()
        assertThat(state.loading).isFalse()
        assertThat(state.fullText).isEmpty()
    }

    @Test
    fun observeNovels_reflectsImportedNovel() = runBlocking {
        repository.importNovel("shelf_novel.txt", "第一章\n\n内容")

        val novels = dao.observeNovels().first()
        assertThat(novels).hasSize(1)
        assertThat(novels[0].title).isEqualTo("shelf_novel")
    }
}
