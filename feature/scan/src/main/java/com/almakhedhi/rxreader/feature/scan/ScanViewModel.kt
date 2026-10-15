package com.almakhedhi.rxreader.feature.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.ai.AiNotConfiguredException
import com.almakhedhi.rxreader.core.ai.AiReader
import com.almakhedhi.rxreader.core.ai.model.AnalysisResult
import com.almakhedhi.rxreader.core.ai.model.AnalyzedDrug
import com.almakhedhi.rxreader.core.scanner.ImagePreprocessor
import com.almakhedhi.rxreader.core.scanner.QualityCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Pipeline state for one prescription analysis. Lives in the shared
 * `scan_graph` scope so the result survives the analyzing -> result hop.
 */
sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data object Working : AnalysisUiState
    data class NeedsDecision(val quality: QualityCheck.Quality) : AnalysisUiState
    data class Failed(val message: String) : AnalysisUiState
    data object Done : AnalysisUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val reader: AiReader,
    private val preprocessor: ImagePreprocessor,
    private val qualityCheck: QualityCheck,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _analysisState =
        MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    private val _selectedDrug = MutableStateFlow<Int?>(null)
    val selectedDrug: StateFlow<Int?> = _selectedDrug.asStateFlow()

    private val _confirmed = MutableStateFlow(false)
    val confirmed: StateFlow<Boolean> = _confirmed.asStateFlow()

    private var startedFor: String? = null
    private var lastUri: String? = null
    private var pendingBitmap: Bitmap? = null
    private var _sourceBitmap: Bitmap? = null

    /** Preprocessed bitmap used for the analysis (for the overlay preview). */
    val sourceBitmap: Bitmap?
        get() = _sourceBitmap

    /**
     * Starts the preprocess -> quality -> AI pipeline for [uriString].
     * Idempotent per URI: recompositions and re-entries reuse the run.
     */
    fun startAnalysis(uriString: String) {
        if (startedFor == uriString) return
        startedFor = uriString
        lastUri = uriString
        _result.value = null
        _confirmed.value = false
        _selectedDrug.value = null
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.Working
            val bitmap = withContext(Dispatchers.Default) {
                preprocessor.preprocess(Uri.parse(uriString))
            }
            if (bitmap == null) {
                _analysisState.value = AnalysisUiState.Failed(
                    context.getString(R.string.analyzing_error_load),
                )
                return@launch
            }
            val quality = withContext(Dispatchers.Default) {
                qualityCheck.assess(bitmap)
            }
            if (quality.isBlurry || quality.isDark) {
                pendingBitmap = bitmap
                _analysisState.value = AnalysisUiState.NeedsDecision(quality)
            } else {
                analyzeBitmap(bitmap)
            }
        }
    }

    /** Continues with the flagged image after the user accepts the warning. */
    fun proceedDespiteWarning() {
        pendingBitmap?.let { analyzeBitmap(it) }
    }

    /** Clears the failure and re-runs the pipeline for the last image. */
    fun retry() {
        startedFor = null
        _result.value = null
        pendingBitmap = null
        lastUri?.let { startAnalysis(it) }
    }

    private fun analyzeBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.Working
            _sourceBitmap = bitmap
            reader.analyze(listOf(bitmap)).fold(
                onSuccess = {
                    _result.value = it
                    _confirmed.value = false
                    _selectedDrug.value = null
                    _analysisState.value = AnalysisUiState.Done
                },
                onFailure = { error ->
                    _analysisState.value =
                        AnalysisUiState.Failed(error.userMessage())
                },
            )
        }
    }

    fun selectDrug(index: Int?) {
        _selectedDrug.value = index
    }

    /** Applies a pharmacist's correction to one medication line. */
    fun updateDrug(index: Int, updated: AnalyzedDrug) {
        val current = _result.value ?: return
        if (index !in current.medications.indices) return
        _result.value = current.copy(
            medications = current.medications.toMutableList()
                .also { it[index] = updated },
        )
    }

    fun confirmAll() {
        _confirmed.value = true
    }

    private fun Throwable.userMessage(): String =
        if (this is AiNotConfiguredException) {
            context.getString(R.string.analyzing_error_config)
        } else {
            message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.analyzing_error_generic)
        }
}
