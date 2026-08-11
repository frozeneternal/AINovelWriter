package com.ainovel.app.data.repository

import com.ainovel.app.data.local.dao.NovelDao
import com.ainovel.app.data.local.entity.HistoryRecordEntity
import com.ainovel.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: NovelDao
) {
    fun observeHistory(): Flow<List<HistoryRecordEntity>> = dao.observeHistory()

    suspend fun getHistoryForNovel(novelId: Long): List<HistoryRecordEntity> =
        dao.getHistoryForNovel(novelId)

    suspend fun recordSuccess(
        novelId: Long?,
        agentRole: String,
        inputSummary: String,
        outputText: String
    ): Long {
        return dao.insertHistory(
            HistoryRecordEntity(
                novelId = novelId,
                agentRole = agentRole,
                inputSummary = inputSummary,
                outputText = outputText,
                status = TaskStatus.SUCCESS
            )
        )
    }

    suspend fun recordFailure(
        novelId: Long?,
        agentRole: String,
        inputSummary: String,
        errorMessage: String
    ): Long {
        return dao.insertHistory(
            HistoryRecordEntity(
                novelId = novelId,
                agentRole = agentRole,
                inputSummary = inputSummary,
                outputText = "",
                status = TaskStatus.FAILED,
                errorMessage = errorMessage
            )
        )
    }

    suspend fun deleteHistory(id: Long) = dao.deleteHistory(id)

    suspend fun clearHistory() = dao.clearHistory()
}
