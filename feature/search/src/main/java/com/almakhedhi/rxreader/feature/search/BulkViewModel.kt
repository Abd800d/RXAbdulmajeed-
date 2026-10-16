package com.almakhedhi.rxreader.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.work.EnrichWork
import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.datastore.EnrichPrefs
import com.almakhedhi.rxreader.core.datastore.EnrichSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BulkReport(
    val done: Int,
    val enriched: Int,
    val images: Int,
    val aiDone: Int,
    val aiSkipped: Boolean,
)

data class BulkUiState(
    val candidates: List<DrugEntity> = emptyList(),
    val loading: Boolean = true,
    val selected: Set<Long> = emptySet(),
    val withAi: Boolean = false,
    val prefs: EnrichSettings = EnrichSettings(),
    val confirming: Boolean = false,
    val running: Boolean = false,
    val progress: Float = 0f,
    val report: BulkReport? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class BulkViewModel @Inject constructor(
    private val drugs: DrugRepository,
    private val prefs: EnrichPrefs,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(BulkUiState())
    val state: StateFlow<BulkUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _state.update { it.copy(prefs = settings) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val all = runCatching {
                drugs.listPage(DrugFilter(), CANDIDATE_LIMIT, 0)
            }.getOrDefault(emptyList())
            val incomplete = all.filter { it.deletedAt == null && isIncomplete(it) }
            _state.update {
                it.copy(
                    candidates = incomplete,
                    loading = false,
                    selected = it.selected.intersect(incomplete.map { d -> d.id }.toSet()),
                )
            }
        }
    }

    private fun isIncomplete(drug: DrugEntity): Boolean =
        drug.scientific.isNullOrBlank() ||
            drug.manufacturer.isNullOrBlank() ||
            drug.indications.isNullOrBlank() ||
            drug.dosageAdults.isNullOrBlank()

    fun toggle(id: Long) {
        _state.update {
            val next = it.selected.toMutableSet()
            if (!next.add(id)) next.remove(id)
            it.copy(selected = next)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selected = it.candidates.map { d -> d.id }.toSet()) }
    }

    fun selectNone() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun setWithAi(enabled: Boolean) {
        _state.update { it.copy(withAi = enabled) }
    }

    fun askConfirm() {
        if (_state.value.selected.isEmpty() || _state.value.running) return
        _state.update { it.copy(confirming = true) }
    }

    fun cancelConfirm() {
        _state.update { it.copy(confirming = false) }
    }

    fun start() {
        val current = _state.value
        if (current.selected.isEmpty() || current.running) return
        viewModelScope.launch {
            _state.update {
                it.copy(confirming = false, running = true, progress = 0f, report = null, failed = false)
            }
            EnrichWork.enqueueBulk(
                appContext,
                current.selected.toList(),
                withImages = true,
                withAi = current.withAi,
                wifiOnly = current.prefs.wifiOnly,
            )
            WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow(EnrichWork.BULK_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    if (info.state.isFinished) {
                        if (info.state == WorkInfo.State.SUCCEEDED) {
                            val out = info.outputData
                            _state.update {
                                it.copy(
                                    running = false,
                                    report = BulkReport(
                                        done = out.getInt(EnrichWork.OUT_DONE, 0),
                                        enriched = out.getInt(EnrichWork.OUT_ENRICHED, 0),
                                        images = out.getInt(EnrichWork.OUT_IMAGES, 0),
                                        aiDone = out.getInt(EnrichWork.OUT_AI, 0),
                                        aiSkipped = out.getBoolean(EnrichWork.OUT_AI_SKIPPED, false),
                                    ),
                                )
                            }
                        } else {
                            _state.update { it.copy(running = false, failed = true) }
                        }
                        refresh()
                        return@launch
                    } else {
                        _state.update {
                            it.copy(progress = info.progress.getFloat(EnrichWork.PROGRESS, 0f))
                        }
                    }
                }
        }
    }

    fun consumeReport() {
        _state.update { it.copy(report = null, failed = false) }
    }

    companion object {
        const val CANDIDATE_LIMIT = 500
    }
}
