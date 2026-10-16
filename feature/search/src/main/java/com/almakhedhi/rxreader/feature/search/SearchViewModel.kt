package com.almakhedhi.rxreader.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.ai.AiReader
import com.almakhedhi.rxreader.core.data.model.DrugDraft
import com.almakhedhi.rxreader.core.data.model.DrugMatch
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.repo.EnrichImage
import com.almakhedhi.rxreader.core.data.repo.EnrichmentRepository
import com.almakhedhi.rxreader.core.data.repo.OfficialDetails
import com.almakhedhi.rxreader.core.data.repo.OfficialMatch
import com.almakhedhi.rxreader.core.datastore.EnrichMode
import com.almakhedhi.rxreader.core.datastore.EnrichPrefs
import com.almakhedhi.rxreader.core.datastore.EnrichSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AiSuggestion(val name: String, val reason: String)

data class OfficialSelection(
    val match: OfficialMatch,
    val details: OfficialDetails? = null,
    val images: List<EnrichImage> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

data class SearchUiState(
    val query: String = "",
    val local: List<DrugMatch> = emptyList(),
    val localLoading: Boolean = false,
    val official: List<OfficialMatch> = emptyList(),
    val officialLoading: Boolean = false,
    val officialFailed: Boolean = false,
    val officialFetched: Boolean = false,
    val selection: OfficialSelection? = null,
    val saving: Boolean = false,
    val savedDrugId: Long? = null,
    val ai: List<AiSuggestion> = emptyList(),
    val aiLoading: Boolean = false,
    val aiFailed: Boolean = false,
    val aiFetched: Boolean = false,
    val aiQuotaDenied: Boolean = false,
    val prefs: EnrichSettings? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val drugs: DrugRepository,
    private val enrich: EnrichmentRepository,
    private val reader: AiReader,
    private val prefs: EnrichPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _state.update { it.copy(prefs = settings) }
            }
        }
        viewModelScope.launch {
            queryFlow.debounce(400).distinctUntilChanged().collectLatest { q ->
                runLocal(q)
                _state.update {
                    it.copy(
                        official = emptyList(),
                        officialFetched = false,
                        officialFailed = false,
                        selection = null,
                        ai = emptyList(),
                        aiFetched = false,
                        aiFailed = false,
                        aiQuotaDenied = false,
                        savedDrugId = null,
                    )
                }
                if (q.isNotBlank() && _state.value.prefs?.mode == EnrichMode.AUTO) {
                    loadOfficial()
                }
            }
        }
    }

    fun setQuery(query: String) {
        queryFlow.value = query
        _state.update { it.copy(query = query) }
    }

    private suspend fun runLocal(q: String) {
        if (q.isBlank()) {
            _state.update { it.copy(local = emptyList(), localLoading = false) }
            return
        }
        _state.update { it.copy(localLoading = true) }
        val hits = runCatching { drugs.findMatches(q, LOCAL_LIMIT) }
            .getOrDefault(emptyList())
        _state.update { it.copy(local = hits, localLoading = false) }
    }

    fun loadOfficial() {
        val q = _state.value.query.trim()
        if (q.length < 2 || _state.value.officialLoading) return
        viewModelScope.launch {
            _state.update { it.copy(officialLoading = true, officialFailed = false) }
            val result = runCatching { enrich.searchOfficial(q) }
            _state.update {
                it.copy(
                    officialLoading = false,
                    officialFetched = true,
                    official = result.getOrDefault(emptyList()),
                    officialFailed = result.isFailure,
                )
            }
        }
    }

    fun selectOfficial(match: OfficialMatch) {
        viewModelScope.launch {
            _state.update { it.copy(selection = OfficialSelection(match)) }
            val details = runCatching {
                enrich.officialDetails(match.rxcui, match.name)
            }.getOrNull()
            val images = runCatching {
                enrich.searchImages(match.name, details?.setId).take(IMAGES_SHOWN)
            }.getOrDefault(emptyList())
            _state.update { cur ->
                val sel = cur.selection
                if (sel == null || sel.match != match) {
                    cur
                } else {
                    cur.copy(
                        selection = sel.copy(
                            details = details,
                            images = images,
                            loading = false,
                            failed = details == null,
                        ),
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = null) }
    }

    /** One-tap save: official fields + up to 3 web images, reviewed = true. */
    fun saveOfficial() {
        val sel = _state.value.selection ?: return
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val d = sel.details
            val draft = DrugDraft(
                tradeEn = d?.brandNames?.firstOrNull() ?: sel.match.name,
                scientific = d?.genericName ?: sel.match.name,
                actives = d?.substance,
                route = d?.route,
                dosageForm = d?.dosageForm,
                manufacturer = d?.manufacturer,
                packSize = d?.packaging,
                indications = d?.indications,
                dosageAdults = d?.dosage,
                contraindications = d?.contraindications,
                sideEffects = d?.adverse,
                interactions = d?.interactions,
                warnings = d?.warnings,
                rxNormId = d?.rxcui ?: sel.match.rxcui,
                dailymedId = d?.setId,
                reviewed = true,
            )
            val id = runCatching { drugs.save(draft) }.getOrNull()
            if (id != null) {
                sel.images.take(IMAGES_SAVED).forEach { image ->
                    runCatching { enrich.downloadToDrug(id, image) }
                }
            }
            _state.update { it.copy(saving = false, savedDrugId = id) }
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedDrugId = null) }
    }

    fun askAi() {
        val q = _state.value.query.trim()
        if (q.length < 2 || _state.value.aiLoading) return
        viewModelScope.launch {
            _state.update { it.copy(aiLoading = true, aiFailed = false, aiQuotaDenied = false) }
            if (!prefs.tryConsumeAi(1)) {
                _state.update { it.copy(aiLoading = false, aiFetched = true, aiQuotaDenied = true) }
                return@launch
            }
            val result = runCatching {
                val raw = reader.generateJsonText(AI_SYSTEM, AI_USER.format(q)).getOrThrow()
                parseSuggestions(raw)
            }
            _state.update {
                it.copy(
                    aiLoading = false,
                    aiFetched = true,
                    ai = result.getOrDefault(emptyList()),
                    aiFailed = result.isFailure,
                )
            }
        }
    }

    private fun parseSuggestions(raw: String): List<AiSuggestion> {
        val arr = JSONObject(raw).optJSONArray("suggestions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isEmpty()) null else AiSuggestion(name, o.optString("reason").trim())
        }.take(AI_LIMIT)
    }

    companion object {
        const val LOCAL_LIMIT = 10
        const val IMAGES_SHOWN = 6
        const val IMAGES_SAVED = 3
        const val AI_LIMIT = 5
        const val AI_SYSTEM =
            "You are a pharmacy search assistant. Answer with a single JSON object only, no markdown."
        const val AI_USER =
            "The user searches a drug catalog for \"%1\$s\" and found nothing. " +
                "Suggest up to 5 likely drug names (correct spelling, Arabic or English). " +
                "Return JSON: {\"suggestions\":[{\"name\":\"...\",\"reason\":\"...\"}]}."
    }
}
