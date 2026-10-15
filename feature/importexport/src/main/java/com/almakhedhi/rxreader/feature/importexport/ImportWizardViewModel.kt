package com.almakhedhi.rxreader.feature.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.ImportExportRepository
import com.almakhedhi.rxreader.core.database.entity.ImportJobEntity
import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import com.almakhedhi.rxreader.core.files.csv.CsvCodec
import com.almakhedhi.rxreader.core.files.ingest.ImportEngine
import com.almakhedhi.rxreader.core.files.json.JsonDrug
import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec.toFieldMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WizardStep { SOURCE, FORMAT, MAPPING, VALIDATION, PROGRESS }

data class ImportWizardUiState(
    val step: WizardStep = WizardStep.SOURCE,
    val fileName: String = "",
    val format: ImportExportRepository.Format? = null,
    val parsed: ImportExportRepository.ParsedTable? = null,
    val jsonCount: Int = 0,
    val jsonVersion: Int? = null,
    val mapping: Map<Int, String> = emptyMap(),
    val validation: ImportEngine.Validation? = null,
    val policy: ImportEngine.Policy = ImportEngine.Policy.SKIP,
    val progress: ImportEngine.Progress? = null,
    val report: ImportEngine.Report? = null,
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val recentJobs: List<ImportJobEntity> = emptyList(),
) {
    val isJson: Boolean get() = format == ImportExportRepository.Format.JSON
}

@HiltViewModel
class ImportWizardViewModel @Inject constructor(
    private val repository: ImportExportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportWizardUiState())
    val state: StateFlow<ImportWizardUiState> = _state.asStateFlow()

    private var fileBytes: ByteArray? = null
    private var jsonDrugs: List<JsonDrug>? = null
    private var parsedTable: ImportExportRepository.ParsedTable? = null
    private var encodingOverride: String? = null
    private var delimiterOverride: Char? = null
    private var sheetOverride: String? = null

    init {
        viewModelScope.launch {
            repository.observeRecentJobs().collect { jobs ->
                _state.update { it.copy(recentJobs = jobs) }
            }
        }
    }

    fun loadFile(name: String, bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(busy = true, errorRes = null) }
            try {
                repository.checkSize(bytes)
            } catch (_: IllegalStateException) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_too_big) }
                return@launch
            }
            try {
                val format = repository.detectFormat(name, bytes)
                fileBytes = bytes
                encodingOverride = null
                delimiterOverride = null
                sheetOverride = null
                jsonDrugs = null
                parsedTable = null
                when (format) {
                    ImportExportRepository.Format.JSON -> {
                        val text = runCatching { CsvCodec.decode(bytes).text }
                            .getOrElse { String(bytes, Charsets.UTF_8) }
                        val (drugs, version) = try {
                            repository.parseJson(text)
                        } catch (_: Exception) {
                            _state.update { it.copy(busy = false, errorRes = R.string.err_json) }
                            return@launch
                        }
                        if (drugs.isEmpty()) {
                            _state.update { it.copy(busy = false, errorRes = R.string.err_empty) }
                            return@launch
                        }
                        jsonDrugs = drugs
                        _state.update {
                            it.copy(
                                busy = false,
                                fileName = name,
                                format = format,
                                jsonCount = drugs.size,
                                jsonVersion = version,
                                step = WizardStep.FORMAT,
                            )
                        }
                    }
                    ImportExportRepository.Format.CSV -> parseCsv(name, format)
                    ImportExportRepository.Format.XLSX -> parseXlsx(name, format)
                }
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_unreadable) }
            }
        }
    }

    fun loadSheetsUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true, errorRes = null) }
            try {
                val bytes = repository.downloadSheetsCsv(url.trim())
                loadFile("sheets.csv", bytes)
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_network) }
            }
        }
    }

    private fun parseCsv(name: String, format: ImportExportRepository.Format) {
        val bytes = fileBytes ?: return
        val table = repository.parseCsv(bytes, delimiterOverride, encodingOverride)
        if (table.header.isEmpty()) {
            _state.update { it.copy(busy = false, errorRes = R.string.err_empty) }
            return
        }
        parsedTable = table
        _state.update {
            it.copy(
                busy = false,
                fileName = name,
                format = format,
                parsed = table,
                mapping = table.mapping.map,
                step = WizardStep.FORMAT,
            )
        }
    }

    private fun parseXlsx(name: String, format: ImportExportRepository.Format) {
        val bytes = fileBytes ?: return
        val table = repository.parseXlsx(bytes, sheetOverride)
        if (table.header.isEmpty()) {
            _state.update { it.copy(busy = false, errorRes = R.string.err_empty) }
            return
        }
        parsedTable = table
        _state.update {
            it.copy(
                busy = false,
                fileName = name,
                format = format,
                parsed = table,
                mapping = table.mapping.map,
                step = WizardStep.FORMAT,
            )
        }
    }

    fun setEncoding(encoding: String?) {
        encodingOverride = encoding
        val name = _state.value.fileName
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(busy = true) }
            try {
                parseCsvKeepStep(name)
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_unreadable) }
            }
        }
    }

    fun setDelimiter(delimiter: Char?) {
        delimiterOverride = delimiter
        val name = _state.value.fileName
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(busy = true) }
            try {
                parseCsvKeepStep(name)
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_unreadable) }
            }
        }
    }

    fun setSheet(sheet: String) {
        sheetOverride = sheet
        val name = _state.value.fileName
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(busy = true) }
            try {
                val bytes = fileBytes ?: return@launch
                val table = repository.parseXlsx(bytes, sheet)
                parsedTable = table
                _state.update {
                    it.copy(busy = false, parsed = table, mapping = table.mapping.map)
                }
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_unreadable) }
            }
        }
    }

    private fun parseCsvKeepStep(name: String) {
        val bytes = fileBytes ?: return
        val table = repository.parseCsv(bytes, delimiterOverride, encodingOverride)
        parsedTable = table
        _state.update { it.copy(busy = false, parsed = table, mapping = table.mapping.map) }
    }

    fun setMapping(column: Int, fieldId: String?) {
        _state.update {
            val next = it.mapping.toMutableMap()
            if (fieldId == null) next.remove(column) else next[column] = fieldId
            it.copy(mapping = next, errorRes = null)
        }
    }

    fun setPolicy(policy: ImportEngine.Policy) {
        _state.update { it.copy(policy = policy) }
    }

    fun next() {
        val current = _state.value
        when (current.step) {
            WizardStep.SOURCE -> return
            WizardStep.FORMAT -> {
                if (current.isJson) {
                    _state.update { it.copy(step = WizardStep.VALIDATION, busy = true) }
                    runValidation()
                } else {
                    _state.update { it.copy(step = WizardStep.MAPPING) }
                }
            }
            WizardStep.MAPPING -> {
                val hasName = current.mapping.values.any { it in DrugColumns.NAME_FIELDS }
                if (!hasName) {
                    _state.update { it.copy(errorRes = R.string.map_need_name) }
                    return
                }
                _state.update { it.copy(step = WizardStep.VALIDATION, busy = true) }
                runValidation()
            }
            WizardStep.VALIDATION,
            WizardStep.PROGRESS,
                -> return
        }
    }

    fun back() {
        val current = _state.value
        if (current.busy) return
        if (current.step == WizardStep.PROGRESS && current.report == null) return
        _state.update {
            it.copy(
                step = when (it.step) {
                    WizardStep.SOURCE -> WizardStep.SOURCE
                    WizardStep.FORMAT -> WizardStep.SOURCE
                    WizardStep.MAPPING -> WizardStep.FORMAT
                    WizardStep.VALIDATION -> if (it.isJson) WizardStep.FORMAT else WizardStep.MAPPING
                    WizardStep.PROGRESS -> WizardStep.VALIDATION
                },
                errorRes = null,
            )
        }
    }

    private fun runValidation() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val validation = if (_state.value.isJson) {
                    val maps = (jsonDrugs ?: emptyList()).map { it.toFieldMap() }
                    repository.validateJson(maps)
                } else {
                    val table = parsedTable ?: return@launch
                    repository.validate(table.rows, _state.value.mapping)
                }
                _state.update { it.copy(busy = false, validation = validation) }
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_unreadable) }
            }
        }
    }

    fun startImport() {
        val current = _state.value
        if (current.busy || current.validation == null) return
        _state.update { it.copy(step = WizardStep.PROGRESS, busy = true, progress = null, report = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val report = if (current.isJson) {
                    repository.importJson(
                        current.fileName,
                        jsonDrugs ?: emptyList(),
                        current.policy,
                    ) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                } else {
                    val table = parsedTable ?: return@launch
                    repository.runImport(
                        current.fileName,
                        current.format ?: ImportExportRepository.Format.CSV,
                        table.rows,
                        current.mapping,
                        current.policy,
                    ) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                }
                _state.update { it.copy(busy = false, report = report) }
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.err_unreadable) }
            }
        }
    }

    fun reset() {
        fileBytes = null
        jsonDrugs = null
        parsedTable = null
        encodingOverride = null
        delimiterOverride = null
        sheetOverride = null
        _state.update {
            ImportWizardUiState(recentJobs = it.recentJobs)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorRes = null) }
    }
}
