package com.ainovel.app.domain.usecase

import com.ainovel.app.domain.analysis.AnalysisEvent
import com.ainovel.app.domain.analysis.AnalysisSession
import com.ainovel.app.domain.analysis.NovelAnalyzer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelAnalysisUseCase @Inject constructor(
    private val analyzer: NovelAnalyzer
) {

    private val sessions = mutableMapOf<Long, AnalysisSession>()

    fun getSession(novelId: Long): AnalysisSession? = sessions[novelId]

    fun analyzeNovel(novelId: Long, fullText: String): Flow<AnalysisEvent> {
        val session = AnalysisSession(novelId)
        sessions[novelId] = session
        return analyzer.analyze(
            request = com.ainovel.app.domain.analysis.AnalysisRequest(novelId, fullText),
            session = session
        )
    }

    fun resetSession(novelId: Long) {
        sessions.remove(novelId)
    }

    fun cancel(novelId: Long) {
        sessions[novelId]?.reset()
        sessions.remove(novelId)
    }
}
