package com.ainovel.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "generated_assets", indices = [Index("novelId")])
data class GeneratedAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long? = null,
    val chapterId: Long? = null,
    val type: String,
    val localPath: String,
    val prompt: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
