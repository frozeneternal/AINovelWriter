package com.ainovel.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.ChatMessageEntity
import com.ainovel.app.data.local.entity.GeneratedAssetEntity
import com.ainovel.app.data.local.entity.HistoryRecordEntity
import com.ainovel.app.data.local.entity.ImportedTextEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.local.entity.OutlineEntity
import com.ainovel.app.data.local.entity.WorldviewEntity

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        WorldviewEntity::class,
        OutlineEntity::class,
        HistoryRecordEntity::class,
        GeneratedAssetEntity::class,
        ChatMessageEntity::class,
        ImportedTextEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
}
