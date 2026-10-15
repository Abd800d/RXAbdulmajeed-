package com.almakhedhi.rxreader.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.repo.PrescriptionRepository
import com.almakhedhi.rxreader.core.database.entity.PrescriptionSummary
import com.almakhedhi.rxreader.core.database.entity.PrescriptionWithItems
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: PrescriptionRepository,
) : ViewModel() {

    val summaries: StateFlow<List<PrescriptionSummary>> =
        repository.observeSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

data class HistoryDetailUiState(
    val detail: PrescriptionWithItems? = null,
    val matchNames: Map<Long, String> = emptyMap(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    private val repository: PrescriptionRepository,
    private val drugs: DrugRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryDetailUiState())
    val state: StateFlow<HistoryDetailUiState> = _state.asStateFlow()

    private var prescriptionId: Long = -1

    fun load(id: Long) {
        if (id == prescriptionId) return
        prescriptionId = id
        viewModelScope.launch {
            val detail = repository.getDetail(id)
            val names = detail?.items
                ?.mapNotNull { it.matchedDrugId }
                ?.distinct()
                ?.mapNotNull { drugId ->
                    drugs.getById(drugId)?.let { drug ->
                        drugId to (
                            drug.tradeEn?.takeIf { it.isNotBlank() }
                                ?: drug.tradeAr?.takeIf { it.isNotBlank() }
                                ?: drug.scientific
                                ?: ""
                            )
                    }
                }
                ?.toMap()
                .orEmpty()
            _state.update {
                it.copy(detail = detail, matchNames = names, loading = false)
            }
        }
    }

    fun imageFile(name: String): File = repository.imageFile(name)

    fun delete() {
        viewModelScope.launch {
            repository.delete(prescriptionId)
            _state.update { it.copy(deleted = true) }
        }
    }
}
