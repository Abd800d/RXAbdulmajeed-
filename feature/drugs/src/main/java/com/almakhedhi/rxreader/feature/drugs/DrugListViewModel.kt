package com.almakhedhi.rxreader.feature.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import com.almakhedhi.rxreader.core.database.dao.DrugSort
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrugListUiState(
    val query: String = "",
    val filter: DrugFilter = DrugFilter(),
    val items: List<DrugEntity> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val searching: Boolean = false,
    val values: DrugRepository.FilterValues? = null,
) {
    val hasMore: Boolean get() = !searching && items.size < total
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DrugListViewModel @Inject constructor(
    private val repository: DrugRepository,
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 100
        const val SEARCH_LIMIT = 200
    }

    private val _state = MutableStateFlow(DrugListUiState())
    val state: StateFlow<DrugListUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var depth = PAGE_SIZE

    init {
        viewModelScope.launch {
            _state.update { it.copy(values = repository.filterValues()) }
        }
        // Browse/search reload when the filter, debounced query, or any
        // row write (via MAX(updatedAt)) changes. The text field itself
        // updates immediately through _state in setQuery.
        val filterFlow = _state
            .map { it.filter }
            .distinctUntilChanged()
        val debouncedQuery = queryFlow
            .debounce(300)
            .distinctUntilChanged()
        viewModelScope.launch {
            combine(filterFlow, debouncedQuery, repository.observeMaxUpdated()) { filter, query, _ ->
                filter to query
            }.collectLatest { (filter, query) ->
                if (query.isBlank()) reloadBrowse(filter) else runSearch(query, filter)
            }
        }
    }

    fun setQuery(query: String) {
        queryFlow.value = query
        _state.update { it.copy(query = query) }
    }

    fun setFilter(filter: DrugFilter) {
        depth = PAGE_SIZE
        _state.update { it.copy(filter = filter) }
    }

    fun setSort(sort: DrugSort) {
        setFilter(_state.value.filter.copy(sort = sort))
    }

    fun loadMore() {
        val current = _state.value
        if (current.searching || !current.hasMore || current.loading) return
        depth += PAGE_SIZE
        viewModelScope.launch { reloadBrowse(current.filter) }
    }

    private suspend fun reloadBrowse(filter: DrugFilter) {
        _state.update { it.copy(loading = true, searching = false) }
        val total = repository.count(filter)
        val items = repository.listPage(filter, depth.coerceAtMost(total.coerceAtLeast(1)), 0)
        _state.update { it.copy(items = items, total = total, loading = false) }
    }

    private suspend fun runSearch(query: String, filter: DrugFilter) {
        _state.update { it.copy(loading = true, searching = true) }
        val hits = repository.search(query, filter, SEARCH_LIMIT)
        _state.update { it.copy(items = hits, total = hits.size, loading = false) }
    }
}
