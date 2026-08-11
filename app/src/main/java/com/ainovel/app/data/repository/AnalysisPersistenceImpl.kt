package com.ainovel.app.data.repository

import com.ainovel.app.domain.analysis.AnalysisPersistence
import com.ainovel.app.domain.analysis.SplitChapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisPersistenceImpl @Inject constructor(
    private val novelRepository: NovelRepository
) : AnalysisPersistence {

    override suspend fun saveChapters(novelId: Long, chapters: List<SplitChapter>) {
        novelRepository.saveImportedChapters(
            novelId,
            chapters.mapIndexed { i, c -> (i + 1) to (c.title to c.content) }
        )
    }

    override suspend fun saveCharacters(novelId: Long, charactersText: String) {
        novelRepository.saveAnalysisFields(novelId) { it.copy(characters = charactersText) }
    }

    override suspend fun saveWorldview(novelId: Long, worldviewText: String) {
        novelRepository.mergeWorldviewSections(novelId, worldviewText)
    }

    override suspend fun savePlotAndStyle(novelId: Long, plotSummary: String, styleProfile: String) {
        novelRepository.saveAnalysisFields(novelId) {
            it.copy(plotSummary = plotSummary, styleProfile = styleProfile)
        }
    }
}
