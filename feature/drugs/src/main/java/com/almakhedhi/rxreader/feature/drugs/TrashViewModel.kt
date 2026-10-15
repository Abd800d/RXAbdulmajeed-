package com.almakhedhi.rxreader.feature.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DrugRepository,
) : ViewModel() {

    val items: StateFlow<List<DrugEntity>> =
        repository.observeTrash()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Auto-purge items older than the 30-day retention (PLAN §4.4).
        viewModelScope.launch {
            repository.purgeExpiredTrash()
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { repository.deleteForever(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }
}
