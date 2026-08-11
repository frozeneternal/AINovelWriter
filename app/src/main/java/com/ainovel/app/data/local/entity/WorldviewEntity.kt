package com.ainovel.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "worldviews",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId")]
)
data class WorldviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val characters: String = "",
    val geography: String = "",
    val rules: String = "",
    val timeline: String = "",
    val plotSummary: String = "",
    val styleProfile: String = "",
    val rawText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
