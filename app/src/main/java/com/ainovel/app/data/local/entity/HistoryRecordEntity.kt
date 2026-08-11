package com.ainovel.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ainovel.app.domain.model.TaskStatus

@Entity(tableName = "history_records", indices = [Index("novelId"), Index("createdAt")])
data class HistoryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long? = null,
    val agentRole: String,
    val inputSummary: String,
    val outputText: String,
    val status: TaskStatus = TaskStatus.SUCCESS,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
