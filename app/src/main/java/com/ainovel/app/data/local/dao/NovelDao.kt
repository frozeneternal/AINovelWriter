package com.ainovel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.ChatMessageEntity
import com.ainovel.app.data.local.entity.GeneratedAssetEntity
import com.ainovel.app.data.local.entity.HistoryRecordEntity
import com.ainovel.app.data.local.entity.ImportedTextEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.local.entity.OutlineEntity
import com.ainovel.app.data.local.entity.WorldviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels ORDER BY updatedAt DESC")
    fun observeNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActiveNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeletedNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id")
    fun observeNovel(id: Long): Flow<NovelEntity?>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getNovel(id: Long): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity): Long

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Delete
    suspend fun deleteNovel(novel: NovelEntity)

    @Query("DELETE FROM novels WHERE id = :novelId")
    suspend fun deleteNovelById(novelId: Long)

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteChaptersByNovel(novelId: Long)

    @Query("DELETE FROM worldviews WHERE novelId = :novelId")
    suspend fun deleteWorldviewByNovel(novelId: Long)

    @Query("DELETE FROM outlines WHERE novelId = :novelId")
    suspend fun deleteOutlineByNovel(novelId: Long)

    @Query("DELETE FROM imported_texts WHERE novelId = :novelId")
    suspend fun deleteImportedTextByNovel(novelId: Long)

    @Query("DELETE FROM chat_messages WHERE novelId = :novelId")
    suspend fun deleteChatMessagesByNovel(novelId: Long)

    @Query("DELETE FROM generated_assets WHERE novelId = :novelId")
    suspend fun deleteAssetsByNovel(novelId: Long)

    @Query("DELETE FROM history_records WHERE novelId = :novelId")
    suspend fun deleteHistoryByNovel(novelId: Long)

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY indexInNovel ASC")
    fun observeChapters(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY indexInNovel ASC")
    suspend fun getChapters(novelId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapter(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND indexInNovel = :index")
    suspend fun getChapterByIndex(novelId: Long, index: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId")
    suspend fun countChapters(novelId: Long): Int

    @Query("SELECT * FROM worldviews WHERE novelId = :novelId LIMIT 1")
    suspend fun getWorldview(novelId: Long): WorldviewEntity?

    @Query("SELECT * FROM worldviews WHERE novelId = :novelId LIMIT 1")
    fun observeWorldview(novelId: Long): Flow<WorldviewEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorldview(worldview: WorldviewEntity): Long

    @Query("SELECT * FROM imported_texts WHERE novelId = :novelId LIMIT 1")
    suspend fun getImportedText(novelId: Long): ImportedTextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedText(text: ImportedTextEntity): Long

    @Query("SELECT * FROM outlines WHERE novelId = :novelId LIMIT 1")
    suspend fun getOutline(novelId: Long): OutlineEntity?

    @Query("SELECT * FROM outlines WHERE novelId = :novelId LIMIT 1")
    fun observeOutline(novelId: Long): Flow<OutlineEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutline(outline: OutlineEntity): Long

    @Query("SELECT * FROM chat_messages WHERE novelId = :novelId ORDER BY createdAt ASC")
    suspend fun getChatMessages(novelId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE novelId = :novelId")
    suspend fun clearChatMessages(novelId: Long)

    @Query("SELECT * FROM generated_assets WHERE novelId = :novelId ORDER BY createdAt DESC")
    suspend fun getAssets(novelId: Long): List<GeneratedAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: GeneratedAssetEntity): Long

    @Query("SELECT * FROM history_records ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<HistoryRecordEntity>>

    @Query("SELECT * FROM history_records WHERE novelId = :novelId ORDER BY createdAt DESC")
    suspend fun getHistoryForNovel(novelId: Long): List<HistoryRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: HistoryRecordEntity): Long

    @Query("DELETE FROM history_records WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM history_records")
    suspend fun clearHistory()
}
