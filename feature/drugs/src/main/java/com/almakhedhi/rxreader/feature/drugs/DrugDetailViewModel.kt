package com.almakhedhi.rxreader.feature.drugs

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.almakhedhi.rxreader.core.data.enrich.DrugCardReader
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.repo.EnrichImage
import com.almakhedhi.rxreader.core.data.repo.EnrichmentRepository
import com.almakhedhi.rxreader.core.data.repo.ImageRepository
import com.almakhedhi.rxreader.core.data.work.EnrichWork
import com.almakhedhi.rxreader.core.database.entity.DrugAliasEntity
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.database.entity.DrugImageEntity
import com.almakhedhi.rxreader.core.datastore.EnrichMode
import com.almakhedhi.rxreader.core.datastore.EnrichPrefs
import com.almakhedhi.rxreader.core.datastore.EnrichSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnrichReport(
    val enriched: Boolean,
    val images: Int,
    val aiDone: Int,
    val aiSkipped: Boolean,
)

data class DrugDetailUiState(
    val drug: DrugEntity? = null,
    val aliases: List<DrugAliasEntity> = emptyList(),
    val images: List<DrugImageEntity> = emptyList(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val aliasAdded: Boolean? = null,
    val prefs: EnrichSettings = EnrichSettings(),
    val enriching: Boolean = false,
    val enrichProgress: Float = 0f,
    val enrichReport: EnrichReport? = null,
    val enrichFailed: Boolean = false,
    val aiRunning: Boolean = false,
    val aiReport: DrugCardReader.CardReport? = null,
    val aiFailed: Boolean = false,
    val webImages: List<EnrichImage> = emptyList(),
    val webLoading: Boolean = false,
    val downloading: Set<String> = emptySet(),
)

@HiltViewModel
class DrugDetailViewModel @Inject constructor(
    private val repository: DrugRepository,
    private val images: ImageRepository,
    private val enrich: EnrichmentRepository,
    private val cards: DrugCardReader,
    private val prefs: EnrichPrefs,
    @ApplicationContext private val appContext: Context,
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
                images.observeImages(id),
                prefs.settings,
            ) { drug, aliases, imgs, settings ->
                _state.value.copy(
                    drug = drug,
                    aliases = aliases,
                    images = imgs,
                    loading = false,
                    prefs = settings,
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

    // Gallery.

    fun imageFile(fileName: String): java.io.File = images.fileFor(fileName)

    fun attachUris(uris: List<Uri>) {
        viewModelScope.launch {
            uris.take(MAX_ATTACH).forEach { uri ->
                runCatching {
                    val bytes = appContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?.takeIf { it.size <= MAX_ATTACH_BYTES }
                        ?: return@forEach
                    images.addLocalImage(drugId, bytes)
                }
            }
        }
    }

    fun attachBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            runCatching {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                images.addLocalImage(drugId, stream.toByteArray())
            }
        }
    }

    fun deleteImage(id: Long) {
        viewModelScope.launch {
            runCatching { images.deleteImage(id) }
        }
    }

    // Official enrichment (single drug, background worker with live progress).

    fun enrich() {
        if (_state.value.enriching || _state.value.prefs.mode == EnrichMode.OFF) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    enriching = true,
                    enrichProgress = 0f,
                    enrichReport = null,
                    enrichFailed = false,
                )
            }
            val uuid = EnrichWork.enqueueSingle(
                appContext,
                drugId,
                withImages = true,
                withAi = false,
                wifiOnly = _state.value.prefs.wifiOnly,
            )
            WorkManager.getInstance(appContext).getWorkInfoByIdFlow(uuid).collect { info ->
                if (info == null) return@collect
                if (info.state.isFinished) {
                    if (info.state == WorkInfo.State.SUCCEEDED) {
                        val out = info.outputData
                        _state.update {
                            it.copy(
                                enriching = false,
                                enrichReport = EnrichReport(
                                    enriched = out.getInt(EnrichWork.OUT_ENRICHED, 0) > 0,
                                    images = out.getInt(EnrichWork.OUT_IMAGES, 0),
                                    aiDone = 0,
                                    aiSkipped = false,
                                ),
                            )
                        }
                    } else {
                        _state.update { it.copy(enriching = false, enrichFailed = true) }
                    }
                    return@launch
                } else {
                    _state.update {
                        it.copy(enrichProgress = info.progress.getFloat(EnrichWork.PROGRESS, 0f))
                    }
                }
            }
        }
    }

    fun consumeEnrichReport() {
        _state.update { it.copy(enrichReport = null, enrichFailed = false) }
    }

    // AI completion (foreground: immediate feedback + quota dialog).

    fun aiComplete() {
        if (_state.value.aiRunning) return
        viewModelScope.launch {
            _state.update { it.copy(aiRunning = true, aiReport = null, aiFailed = false) }
            val result = cards.complete(drugId)
            _state.update {
                it.copy(
                    aiRunning = false,
                    aiReport = result.getOrNull(),
                    aiFailed = result.isFailure,
                )
            }
        }
    }

    fun consumeAiReport() {
        _state.update { it.copy(aiReport = null, aiFailed = false) }
    }

    // Web images.

    fun loadWebImages() {
        val drug = _state.value.drug ?: return
        val name = drug.tradeEn ?: drug.tradeAr ?: drug.scientific ?: return
        if (_state.value.webLoading) return
        viewModelScope.launch {
            _state.update { it.copy(webLoading = true) }
            val details = runCatching {
                enrich.officialDetails(drug.rxNormId, name)
            }.getOrNull()
            val found = runCatching {
                enrich.searchImages(name, details?.setId).take(WEB_LIMIT)
            }.getOrDefault(emptyList())
            _state.update { it.copy(webLoading = false, webImages = found) }
        }
    }

    fun clearWebImages() {
        _state.update { it.copy(webImages = emptyList()) }
    }

    fun downloadWebImage(image: EnrichImage) {
        viewModelScope.launch {
            _state.update { it.copy(downloading = it.downloading + image.url) }
            runCatching { enrich.downloadToDrug(drugId, image) }
            _state.update { it.copy(downloading = it.downloading - image.url) }
        }
    }

    companion object {
        const val MAX_ATTACH = 10
        const val MAX_ATTACH_BYTES = 20 * 1024 * 1024
        const val WEB_LIMIT = 10
    }
}
