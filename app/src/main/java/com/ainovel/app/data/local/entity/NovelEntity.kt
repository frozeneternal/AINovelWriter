package com.ainovel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val synopsis: String,
    val genre: String,
    val coverPath: String? = null,
    val worldviewId: Long? = null,
    val outlineId: Long? = null,
    val status: NovelStatus = NovelStatus.DRAFT,
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val source: NovelSource = NovelSource.ORIGINAL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val lastDirection: String = "",
    val lastChapterWordCount: Int = 0
)
