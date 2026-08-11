package com.ainovel.app.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val novelRepository: NovelRepository
) : ViewModel() {

    val novels: StateFlow<List<NovelEntity>> = novelRepository.observeNovels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun deleteNovel(novelId: Long) {
        viewModelScope.launch {
            novelRepository.getNovel(novelId)?.let { novelRepository.deleteNovel(it) }
        }
    }
}
