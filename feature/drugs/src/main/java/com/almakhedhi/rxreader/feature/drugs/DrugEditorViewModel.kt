package com.almakhedhi.rxreader.feature.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.model.DrugDraft
import com.almakhedhi.rxreader.core.data.model.toDraft
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrugEditorUiState(
    val draft: DrugDraft = DrugDraft(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val savedId: Long? = null,
    val nameError: Boolean = false,
)

@HiltViewModel
class DrugEditorViewModel @Inject constructor(
    private val repository: DrugRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DrugEditorUiState())
    val state: StateFlow<DrugEditorUiState> = _state.asStateFlow()

    private var drugId: Long = -1

    fun load(id: Long) {
        if (id == drugId) return
        drugId = id
        if (id <= 0) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val drug = repository.getById(id)
            _state.update { it.copy(draft = drug?.toDraft() ?: DrugDraft(), loading = false) }
        }
    }

    fun update(transform: (DrugDraft) -> DrugDraft) {
        _state.update { it.copy(draft = transform(it.draft), nameError = false) }
    }

    fun save() {
        val draft = _state.value.draft
        val hasName = !draft.tradeEn.isNullOrBlank() ||
            !draft.tradeAr.isNullOrBlank() ||
            !draft.scientific.isNullOrBlank()
        if (!hasName) {
            _state.update { it.copy(nameError = true) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = repository.save(draft)
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }
}
