package com.ainovel.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ainovel.app.data.local.AppDatabase
import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.local.entity.WorldviewEntity
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.agent.DirectionSuggester
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectionSuggesterTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NovelDao
    private lateinit var novelRepository: NovelRepository
    private lateinit var suggester: DirectionSuggester

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedImportedNovel(totalChapters: Int = 3): Long = runBlocking {
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
                styleProfile = "叙事视角：第三人称限知；句式节奏：长短句交错"
            )
        )
        novelId
    }

    @Test
    fun suggest_parsesNumberedDirections() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { _, _, _, _ ->
            "1. 主角解开身世之谜后向帝都进发\n2. 神秘势力登场，主角被迫卷入\n3. 主角选择退隐江湖，调查旧友之死"
        }
        suggester = DirectionSuggester(fake, novelRepository)

        val directions = suggester.suggest(novelId, isContinuation = true)

        assertThat(directions).hasSize(3)
        assertThat(directions[0]).contains("身世之谜")
        assertThat(directions[1]).contains("神秘势力")
        assertThat(directions[2]).contains("退隐江湖")
    }

    @Test
    fun suggest_injectsNovelContextIntoPrompt() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { _, _, _, _ ->
            "1. 方向一\n2. 方向二\n3. 方向三"
        }
        suggester = DirectionSuggester(fake, novelRepository)

        suggester.suggest(novelId, isContinuation = true)

        val prompt = fake.recordedUserMessages.first()
        assertThat(prompt).contains("情节梗概")
        assertThat(prompt).contains("主线：少年成长")
        assertThat(prompt).contains("第三人称限知")
        assertThat(prompt).contains("第 3 章内容")
    }

    @Test
    fun suggest_marksContinuationModeInPrompt() = runBlocking {
        val novelId = seedImportedNovel(2)
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            "1. 方向一\n2. 方向二"
        }
        suggester = DirectionSuggester(fake, novelRepository)

        suggester.suggest(novelId, isContinuation = true)

        assertThat(fake.recordedSystemPrompts.first()).contains("承接最近章节的结尾")
    }

    @Test
    fun suggest_returnsEmptyWhenModelOutputsBlank() = runBlocking {
        val novelId = seedImportedNovel(2)
        val fake = FakeLlmGateway()
        fake.completeHandler = { _, _, _, _ -> "" }
        suggester = DirectionSuggester(fake, novelRepository)

        assertThat(suggester.suggest(novelId, isContinuation = false)).isEmpty()
    }

    @Test
    fun suggest_respectsDisabledSources() = runBlocking {
        val novelId = seedImportedNovel(3)
        val fake = FakeLlmGateway()
        fake.completeHandler = { _, _, _, _ ->
            "1. 方向一\n2. 方向二\n3. 方向三"
        }
        suggester = DirectionSuggester(fake, novelRepository)

        suggester.suggest(
            novelId,
            isContinuation = true,
            sources = com.ainovel.app.domain.agent.DirectionSources(
                includeRecentChapters = false,
                includeCharacters = false,
                includeStyleProfile = false,
                includePlotSummary = false
            )
        )

        val prompt = fake.recordedUserMessages.first()
        assertThat(prompt).doesNotContain("情节梗概")
        assertThat(prompt).doesNotContain("写作手法画像")
        assertThat(prompt).doesNotContain("主要人物")
        assertThat(prompt).doesNotContain("最近章节")
        assertThat(prompt).doesNotContain("主线：少年成长")
        assertThat(prompt).doesNotContain("第三人称限知")
        assertThat(prompt).doesNotContain("主角：阿杰")
        assertThat(prompt).doesNotContain("第 3 章内容")
    }

    @Test
    fun suggest_appendsExtraHintWhenProvided() = runBlocking {
        val novelId = seedImportedNovel(2)
        val fake = FakeLlmGateway()
        fake.completeHandler = { _, _, _, _ ->
            "1. 方向一\n2. 方向二"
        }
        suggester = DirectionSuggester(fake, novelRepository)

        suggester.suggest(
            novelId,
            isContinuation = true,
            sources = com.ainovel.app.domain.agent.DirectionSources(extraHint = "希望方向偏悬疑、少引入新角色")
        )

        val prompt = fake.recordedUserMessages.first()
        assertThat(prompt).contains("用户补充要求")
        assertThat(prompt).contains("希望方向偏悬疑、少引入新角色")
    }
}
