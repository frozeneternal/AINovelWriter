package com.ainovel.app.data.repository

import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.ChatMessageEntity
import com.ainovel.app.data.local.entity.GeneratedAssetEntity
import com.ainovel.app.data.local.entity.ImportedTextEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.local.entity.OutlineEntity
import com.ainovel.app.data.local.entity.WorldviewEntity
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelRepository @Inject constructor(
    private val dao: NovelDao
) {
    fun observeNovels(): Flow<List<NovelEntity>> = dao.observeNovels()
    fun observeNovel(id: Long): Flow<NovelEntity?> = dao.observeNovel(id)
    fun observeChapters(novelId: Long): Flow<List<ChapterEntity>> = dao.observeChapters(novelId)
    fun observeWorldview(novelId: Long): Flow<WorldviewEntity?> = dao.observeWorldview(novelId)
    fun observeOutline(novelId: Long): Flow<OutlineEntity?> = dao.observeOutline(novelId)

    suspend fun getNovel(id: Long): NovelEntity? = dao.getNovel(id)
    suspend fun getChapters(novelId: Long): List<ChapterEntity> = dao.getChapters(novelId)
    suspend fun getChapter(id: Long): ChapterEntity? = dao.getChapter(id)
    suspend fun getChapterByIndex(novelId: Long, index: Int): ChapterEntity? =
        dao.getChapterByIndex(novelId, index)
    suspend fun countChapters(novelId: Long): Int = dao.countChapters(novelId)
    suspend fun getWorldview(novelId: Long): WorldviewEntity? = dao.getWorldview(novelId)
    suspend fun getOutline(novelId: Long): OutlineEntity? = dao.getOutline(novelId)
    suspend fun getAssets(novelId: Long): List<GeneratedAssetEntity> = dao.getAssets(novelId)
    suspend fun getChatMessages(novelId: Long): List<ChatMessageEntity> = dao.getChatMessages(novelId)
    suspend fun getImportedText(novelId: Long): ImportedTextEntity? = dao.getImportedText(novelId)

    suspend fun importNovel(
        fileName: String,
        fullText: String
    ): Long {
        val title = fileName.substringBeforeLast('.').ifBlank { "导入小说" }
        val now = System.currentTimeMillis()
        val novel = NovelEntity(
            title = title,
            synopsis = fullText.take(200),
            genre = "导入",
            status = NovelStatus.DRAFT,
            source = NovelSource.IMPORTED,
            createdAt = now,
            updatedAt = now
        )
        val novelId = dao.insertNovel(novel)
        dao.upsertImportedText(
            ImportedTextEntity(novelId = novelId, fullText = fullText, createdAt = now)
        )
        return novelId
    }

    suspend fun saveImportedChapters(novelId: Long, chapters: List<Pair<Int, Pair<String, String>>>) {
        chapters.forEach { (index, titleContent) ->
            val (title, content) = titleContent
            dao.insertChapter(
                ChapterEntity(
                    novelId = novelId,
                    indexInNovel = index,
                    title = title,
                    content = content,
                    status = com.ainovel.app.domain.model.ChapterStatus.FINAL
                )
            )
        }
        dao.getNovel(novelId)?.let { n ->
            dao.updateNovel(
                n.copy(
                    currentChapterIndex = chapters.size,
                    totalChapters = chapters.size,
                    status = NovelStatus.COMPLETED
                )
            )
        }
    }

    suspend fun saveAnalysisFields(novelId: Long, block: (WorldviewEntity) -> WorldviewEntity) {
        val existing = dao.getWorldview(novelId)
        val wv = block(existing ?: WorldviewEntity(novelId = novelId))
        val id = dao.upsertWorldview(wv)
        dao.getNovel(novelId)?.let { n ->
            if (n.worldviewId == null) dao.updateNovel(n.copy(worldviewId = id))
        }
    }

    /**
     * 合并世界观提取结果：保留已有的人物字段（来自人物提取阶段），
     * 将地理/规则/时间线从小节文本填充。
     */
    suspend fun mergeWorldviewSections(novelId: Long, worldviewText: String) {
        val existing = dao.getWorldview(novelId)
        val parsed = parseWorldview(worldviewText)
        val wv = (existing ?: WorldviewEntity(novelId = novelId)).copy(
            geography = parsed[1],
            rules = parsed[2],
            timeline = parsed[3]
        )
        dao.upsertWorldview(wv)
    }

    suspend fun createNovel(
        title: String,
        synopsis: String,
        genre: String,
        totalChapters: Int
    ): Long {
        val now = System.currentTimeMillis()
        val novel = NovelEntity(
            title = title,
            synopsis = synopsis,
            genre = genre,
            status = NovelStatus.DRAFT,
            totalChapters = totalChapters,
            createdAt = now,
            updatedAt = now
        )
        return dao.insertNovel(novel)
    }

    suspend fun updateNovel(novel: NovelEntity) {
        dao.updateNovel(novel.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateNovelStatus(id: Long, status: NovelStatus) {
        dao.getNovel(id)?.let { dao.updateNovel(it.copy(status = status)) }
    }

    suspend fun deleteNovel(novel: NovelEntity) {
        dao.deleteNovel(novel)
    }

    suspend fun upsertWorldview(novelId: Long, rawText: String) {
        val existing = dao.getWorldview(novelId)
        val (characters, geography, rules, timeline) = parseWorldview(rawText)
        val wv = (existing ?: WorldviewEntity(novelId = novelId)).copy(
            novelId = novelId,
            characters = characters,
            geography = geography,
            rules = rules,
            timeline = timeline,
            rawText = rawText
        )
        val id = dao.upsertWorldview(wv)
        dao.getNovel(novelId)?.let { n ->
            dao.updateNovel(n.copy(worldviewId = id, status = NovelStatus.WORLDVIEW_DONE))
        }
    }

    suspend fun upsertOutline(novelId: Long, content: String) {
        val existing = dao.getOutline(novelId)
        val outline = (existing ?: OutlineEntity(novelId = novelId, content = content)).copy(
            novelId = novelId,
            content = content
        )
        val id = dao.upsertOutline(outline)
        dao.getNovel(novelId)?.let { n ->
            dao.updateNovel(n.copy(outlineId = id, status = NovelStatus.OUTLINED))
        }
    }

    suspend fun saveChapter(
        novelId: Long,
        index: Int,
        title: String,
        content: String,
        summary: String?
    ): Long {
        val existing = dao.getChapterByIndex(novelId, index)
        val chapter = (existing ?: ChapterEntity(
            novelId = novelId,
            indexInNovel = index,
            title = title,
            content = content
        )).copy(
            title = title,
            content = content,
            summary = summary ?: existing?.summary,
            status = com.ainovel.app.domain.model.ChapterStatus.FINAL
        )
        val id = if (existing != null) {
            dao.updateChapter(chapter)
            existing.id
        } else {
            dao.insertChapter(chapter)
        }
        dao.getNovel(novelId)?.let { n ->
            val maxIndex = dao.countChapters(novelId)
            dao.updateNovel(
                n.copy(
                    currentChapterIndex = maxIndex,
                    status = if (maxIndex >= n.totalChapters) NovelStatus.COMPLETED else NovelStatus.WRITING
                )
            )
        }
        return id
    }

    suspend fun updateChapterDraft(chapterId: Long, title: String, content: String) {
        dao.getChapter(chapterId)?.let {
            dao.updateChapter(
                it.copy(
                    title = title,
                    content = content,
                    status = com.ainovel.app.domain.model.ChapterStatus.EDITED
                )
            )
        }
    }

    suspend fun updateChapter(chapter: ChapterEntity) = dao.updateChapter(chapter)

    suspend fun deleteChapter(chapter: ChapterEntity) = dao.deleteChapter(chapter)

    suspend fun addChatMessage(novelId: Long, role: String, content: String) {
        dao.insertChatMessage(
            ChatMessageEntity(novelId = novelId, role = role, content = content)
        )
    }

    suspend fun clearChatMessages(novelId: Long) = dao.clearChatMessages(novelId)

    suspend fun insertAsset(asset: GeneratedAssetEntity): Long = dao.insertAsset(asset)

    private fun parseWorldview(rawText: String): Array<String> {
        val default = Array(4) { "" }
        if (rawText.isBlank()) return default
        val markers = listOf(
            "## 人物设定" to 0,
            "## 地理设定" to 1,
            "## 规则体系" to 2,
            "## 时间线" to 3
        )
        val indices = markers.map { (marker, idx) ->
            val pos = rawText.indexOf(marker)
            pos to idx
        }.filter { it.first >= 0 }.sortedBy { it.first }

        if (indices.isEmpty()) {
            default[0] = rawText
            return default
        }

        indices.forEachIndexed { i, (pos, sectionIdx) ->
            val end = if (i + 1 < indices.size) indices[i + 1].first else rawText.length
            default[sectionIdx] = rawText.substring(pos, end).trim()
        }
        return default
    }
}
