package com.almakhedhi.rxreader.feature.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.database.entity.DrugAliasEntity
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrugDetailUiState(
    val drug: DrugEntity? = null,
    val aliases: List<DrugAliasEntity> = emptyList(),
    val imageCount: Int = 0,
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val aliasAdded: Boolean? = null,
)

@HiltViewModel
class DrugDetailViewModel @Inject constructor(
    private val repository: DrugRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DrugDetailUiState())
    val state: StateFlow<DrugDetailUiState> = _state.asStateFlow()

    private var drugId: Long = -1

    fun load(id: Long) {
        if (id == drugId && !_state.value.loading) return
        drugId = id
        viewModelScope.launch {
            combine(
                repository.observeById(id),
                repository.observeAliases(id),
                repository.observeImages(id),
            ) { drug, aliases, images ->
                DrugDetailUiState(
                    drug = drug,
                    aliases = aliases,
                    imageCount = images.size,
                    loading = false,
                    deleted = _state.value.deleted,
                    aliasAdded = _state.value.aliasAdded,
                )
            }.collect { _state.value = it }
        }
    }

    fun addAlias(raw: String) {
        viewModelScope.launch {
            val added = repository.addAlias(drugId, raw)
            _state.update { it.copy(aliasAdded = added) }
        }
    }

    fun consumeAliasResult() {
        _state.update { it.copy(aliasAdded = null) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.softDelete(drugId)
            _state.update { it.copy(deleted = true) }
        }
    }

    fun removeAlias(aliasId: Long) {
        viewModelScope.launch {
            repository.removeAlias(aliasId, drugId)
        }
    }
}
