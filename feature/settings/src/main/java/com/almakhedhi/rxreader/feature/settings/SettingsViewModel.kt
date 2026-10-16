package com.almakhedhi.rxreader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.database.dao.EnrichCacheDao
import com.almakhedhi.rxreader.core.datastore.EnrichMode
import com.almakhedhi.rxreader.core.datastore.EnrichPrefs
import com.almakhedhi.rxreader.core.datastore.EnrichSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val prefs: EnrichSettings = EnrichSettings(),
    val clearing: Boolean = false,
    val cleared: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: EnrichPrefs,
    private val cache: EnrichCacheDao,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    private data class Transient(val clearing: Boolean = false, val cleared: Boolean = false)

    val state: StateFlow<SettingsUiState> =
        combine(prefs.settings, transient) { p, t ->
            SettingsUiState(prefs = p, clearing = t.clearing, cleared = t.cleared)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setMode(mode: EnrichMode) {
        viewModelScope.launch { prefs.setMode(mode) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { prefs.setWifiOnly(enabled) }
    }

    fun setCap(cap: Int) {
        viewModelScope.launch { prefs.setAiDailyCap(cap) }
    }

    fun clearCache() {
        viewModelScope.launch {
            transient.update { it.copy(clearing = true) }
            runCatching { cache.clearAll() }
            transient.update { it.copy(clearing = false, cleared = true) }
        }
    }

    fun consumeCleared() {
        transient.update { it.copy(cleared = false) }
    }
}
