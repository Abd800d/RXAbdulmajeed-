package com.almakhedhi.rxreader.feature.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.repo.ImportExportRepository
import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportFormat { CSV, XLSX, JSON, ZIP }

data class ExportUiState(
    val filter: DrugFilter = DrugFilter(),
    val values: DrugRepository.FilterValues? = null,
    val count: Int = 0,
    val format: ExportFormat = ExportFormat.CSV,
    val working: Boolean = false,
    val done: Boolean = false,
    val templateSaved: Boolean = false,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ImportExportRepository,
    private val drugs: DrugRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(values = drugs.filterValues()) }
        }
        viewModelScope.launch {
            combine(
                _state,
                drugs.observeMaxUpdated(),
            ) { state, _ -> state.filter }
                .collectLatest { filter ->
                    _state.update { it.copy(count = repository.exportCount(filter)) }
                }
        }
    }

    fun setFilter(filter: DrugFilter) {
        _state.update { it.copy(filter = filter, done = false) }
    }

    fun setFormat(format: ExportFormat) {
        _state.update { it.copy(format = format, done = false) }
    }

    fun export(uri: Uri) {
        val current = _state.value
        if (current.working) return
        _state.update { it.copy(working = true, done = false) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    when (current.format) {
                        ExportFormat.CSV -> repository.exportCsv(out, current.filter)
                        ExportFormat.XLSX -> repository.exportXlsx(out, current.filter)
                        ExportFormat.JSON -> repository.exportJson(out, current.filter)
                        ExportFormat.ZIP -> repository.exportZip(out, current.filter)
                    }
                } ?: throw IllegalStateException("No stream")
            }
            withContext(Dispatchers.Main) {
                _state.update { it.copy(working = false, done = true) }
            }
        }
    }

    fun saveTemplate(format: ExportFormat, uri: Uri) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, templateSaved = false) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    when (format) {
                        ExportFormat.CSV -> repository.writeTemplateCsv(out)
                        ExportFormat.XLSX -> repository.writeTemplateXlsx(out)
                        ExportFormat.JSON -> repository.writeTemplateJson(out)
                        ExportFormat.ZIP -> repository.writeTemplateCsv(out)
                    }
                } ?: throw IllegalStateException("No stream")
            }
            withContext(Dispatchers.Main) {
                _state.update { it.copy(working = false, templateSaved = true) }
            }
        }
    }

    fun saveErrorFile(content: String, uri: Uri, onSaved: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            withContext(Dispatchers.Main) { onSaved() }
        }
    }

    fun consumeDone() {
        _state.update { it.copy(done = false, templateSaved = false) }
    }
}
