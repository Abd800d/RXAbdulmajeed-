package com.almakhedhi.rxreader.feature.importexport

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.data.repo.ImportExportRepository
import com.almakhedhi.rxreader.core.database.entity.ImportJobEntity
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.TealContainer
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary
import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import com.almakhedhi.rxreader.core.files.ingest.ImportEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardScreen(
    viewModel: ImportWizardViewModel,
    exportViewModel: ExportViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    BackHandler(enabled = state.step != WizardStep.SOURCE) { viewModel.back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.wizard_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == WizardStep.SOURCE) onBack() else viewModel.back()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wizard_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            StepIndicator(current = state.step)
            Spacer(modifier = Modifier.height(12.dp))
            state.errorRes?.let { res ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                    }
                }
            }
            when (state.step) {
                WizardStep.SOURCE -> SourceStep(
                    state = state,
                    onPickFile = { name, bytes -> viewModel.loadFile(name, bytes) },
                    onSheets = { viewModel.loadSheetsUrl(it) },
                )
                WizardStep.FORMAT -> FormatStep(
                    state = state,
                    onEncoding = { viewModel.setEncoding(it) },
                    onDelimiter = { viewModel.setDelimiter(it) },
                    onSheet = { viewModel.setSheet(it) },
                    onNext = { viewModel.next() },
                )
                WizardStep.MAPPING -> MappingStep(
                    state = state,
                    onMapping = { col, field -> viewModel.setMapping(col, field) },
                    onNext = { viewModel.next() },
                )
                WizardStep.VALIDATION -> ValidationStep(
                    state = state,
                    onPolicy = { viewModel.setPolicy(it) },
                    onStart = { viewModel.startImport() },
                )
                WizardStep.PROGRESS -> ProgressStep(
                    state = state,
                    onSaveErrors = { content, uri -> exportViewModel.saveErrorFile(content, uri, {}) },
                    onNew = { viewModel.reset() },
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(current: WizardStep) {
    val steps = listOf(
        WizardStep.SOURCE to stringResource(R.string.wizard_step_source),
        WizardStep.FORMAT to stringResource(R.string.wizard_step_format),
        WizardStep.MAPPING to stringResource(R.string.wizard_step_mapping),
        WizardStep.VALIDATION to stringResource(R.string.wizard_step_validation),
        WizardStep.PROGRESS to stringResource(R.string.wizard_step_progress),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEachIndexed { index, (_, label) ->
            val reached = index <= current.ordinal
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (reached) TealContainer else Color.White,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "${index + 1} · $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) TealDark else InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceStep(
    state: ImportWizardUiState,
    onPickFile: (String, ByteArray) -> Unit,
    onSheets: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetsUrl by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = resolver.displayName(uri)
            val bytes = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (bytes != null) onPickFile(name, bytes)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GradientButton(
            text = stringResource(R.string.src_pick),
            onClick = {
                picker.launch(
                    arrayOf(
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/json",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    ),
                )
            },
            icon = Icons.Filled.UploadFile,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.src_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        if (state.busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = TealPrimary,
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.src_sheets_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                OutlinedTextField(
                    value = sheetsUrl,
                    onValueChange = { sheetsUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.src_sheets_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedButton(
                    onClick = { onSheets(sheetsUrl) },
                    enabled = sheetsUrl.isNotBlank() && !state.busy,
                ) {
                    Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.src_sheets_load))
                }
            }
        }
        Text(
            text = stringResource(R.string.src_recent),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = InkPrimary,
        )
        if (state.recentJobs.isEmpty()) {
            Text(
                text = stringResource(R.string.src_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        } else {
            state.recentJobs.forEach { job ->
                RecentJobRow(job = job)
            }
        }
    }
}

@Composable
private fun RecentJobRow(job: ImportJobEntity) {
    val date = remember(job.startedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(job.startedAt))
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Text(
            text = stringResource(
                R.string.src_recent_row,
                date,
                job.added,
                job.updated,
                job.errors,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun FormatStep(
    state: ImportWizardUiState,
    onEncoding: (String?) -> Unit,
    onDelimiter: (Char?) -> Unit,
    onSheet: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.fmt_file, state.fileName),
            style = MaterialTheme.typography.bodyMedium,
            color = InkPrimary,
        )
        Text(
            text = stringResource(R.string.fmt_detected, state.format?.name ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = InkPrimary,
        )
        val parsed = state.parsed
        if (state.isJson) {
            Text(
                text = stringResource(R.string.fmt_json_info, state.jsonVersion ?: 1),
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary,
            )
            Text(
                text = stringResource(R.string.fmt_rows, state.jsonCount),
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary,
            )
        } else if (parsed != null) {
            Text(
                text = stringResource(R.string.fmt_rows, parsed.rows.size),
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary,
            )
            if (state.format == ImportExportRepository.Format.CSV) {
                val autoEncoding = stringResource(R.string.fmt_encoding_auto, parsed.encoding)
                MappingDropdown(
                    label = stringResource(R.string.fmt_encoding),
                    options = listOf("UTF-8", "windows-1256", "windows-1252"),
                    selected = null,
                    allLabel = autoEncoding,
                    onSelect = { onEncoding(it) },
                )
                val delimiterOptions = listOf(",", ";", "Tab", "|")
                MappingDropdown(
                    label = stringResource(R.string.fmt_delimiter),
                    options = delimiterOptions,
                    selected = null,
                    allLabel = stringResource(R.string.fmt_delimiter_auto),
                    onSelect = {
                        onDelimiter(
                            when (it) {
                                "," -> ','
                                ";" -> ';'
                                "Tab" -> '\t'
                                "|" -> '|'
                                else -> null
                            },
                        )
                    },
                )
            }
            if (state.format == ImportExportRepository.Format.XLSX && parsed.sheets.size > 1) {
                MappingDropdown(
                    label = stringResource(R.string.fmt_sheet),
                    options = parsed.sheets,
                    selected = parsed.sheet,
                    onSelect = { it?.let(onSheet) },
                )
            }
            if (state.busy) {
                CircularProgressIndicator(color = TealPrimary)
            }
            Text(
                text = stringResource(R.string.map_preview),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            PreviewTable(
                header = parsed.header,
                rows = parsed.rows.take(5),
                mapping = null,
                onMapping = null,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        GradientButton(
            text = stringResource(R.string.wizard_next),
            onClick = onNext,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MappingStep(
    state: ImportWizardUiState,
    onMapping: (Int, String?) -> Unit,
    onNext: () -> Unit,
) {
    val parsed = state.parsed ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.map_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        val unmapped = parsed.header.filterIndexed { index, _ -> index !in state.mapping }
        if (unmapped.isNotEmpty()) {
            Text(
                text = stringResource(R.string.map_unmapped, unmapped.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            PreviewTable(
                header = parsed.header,
                rows = parsed.rows.take(20),
                mapping = state.mapping,
                onMapping = onMapping,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        GradientButton(
            text = stringResource(R.string.wizard_next),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewTable(
    header: List<String>,
    rows: List<ImportEngine.DataRow>,
    mapping: Map<Int, String>?,
    onMapping: ((Int, String?) -> Unit)?,
) {
    val fieldLabels = remember {
        DrugColumns.ALL.associate { it.id to it }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Row {
                    header.forEachIndexed { index, name ->
                        Column(
                            modifier = Modifier
                                .width(170.dp)
                                .padding(2.dp),
                        ) {
                            Text(
                                text = name.ifBlank { "—" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (mapping != null && onMapping != null) {
                                // Labels are resolved in composition; the callback uses the map.
                                val labelToId = fieldLabels.values.associate { fieldLabel(it) to it.id }
                                val selectedId = mapping[index]
                                val selectedLabel = selectedId?.let { id ->
                                    labelToId.entries.firstOrNull { it.value == id }?.key
                                }
                                MappingDropdown(
                                    label = "",
                                    options = labelToId.keys.toList(),
                                    selected = selectedLabel,
                                    allLabel = stringResource(R.string.map_ignore),
                                    onSelect = { label -> onMapping(index, labelToId[label]) },
                                )
                            }
                        }
                    }
                }
                rows.forEach { row ->
                    Row {
                        row.cells.forEach { cell ->
                            Text(
                                text = cell.ifBlank { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(170.dp)
                                    .padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldLabel(field: DrugColumns.Field): String {
    val language = LocalConfiguration.current.locales[0].language
    return if (language == "ar") field.ar else field.en
}

@Composable
private fun ValidationStep(
    state: ImportWizardUiState,
    onPolicy: (ImportEngine.Policy) -> Unit,
    onStart: () -> Unit,
) {
    val validation = state.validation
    if (state.busy && validation == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = TealPrimary)
        }
        return
    }
    if (validation == null) return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ValidationCount(stringResource(R.string.val_total, validation.total))
        ValidationCount(stringResource(R.string.val_new, validation.fresh))
        ValidationCount(stringResource(R.string.val_duplicates, validation.duplicates))
        ValidationCount(stringResource(R.string.val_invalid, validation.invalidCount))
        ValidationCount(stringResource(R.string.val_empty, validation.empty))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.val_policy),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = InkPrimary,
        )
        PolicyOption(
            label = stringResource(R.string.val_policy_skip),
            selected = state.policy == ImportEngine.Policy.SKIP,
            onClick = { onPolicy(ImportEngine.Policy.SKIP) },
        )
        PolicyOption(
            label = stringResource(R.string.val_policy_update),
            selected = state.policy == ImportEngine.Policy.UPDATE,
            onClick = { onPolicy(ImportEngine.Policy.UPDATE) },
        )
        PolicyOption(
            label = stringResource(R.string.val_policy_merge),
            selected = state.policy == ImportEngine.Policy.MERGE,
            onClick = { onPolicy(ImportEngine.Policy.MERGE) },
        )
        if (validation.invalidSample.isNotEmpty()) {
            Text(
                text = stringResource(R.string.val_problems),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            validation.invalidSample.take(10).forEach { problem ->
                Text(
                    text = stringResource(
                        R.string.val_problem_row,
                        problem.number,
                        problem.reason,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        GradientButton(
            text = stringResource(R.string.val_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ValidationCount(text: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = InkPrimary,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun PolicyOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkPrimary,
        )
    }
}

@Composable
private fun ProgressStep(
    state: ImportWizardUiState,
    onSaveErrors: (String, Uri) -> Unit,
    onNew: () -> Unit,
    onDone: () -> Unit,
) {
    val report = state.report
    val progress = state.progress
    var errorsSaved by remember { mutableStateOf(false) }
    val errorFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        val content = report?.errorCsv
        if (uri != null && content != null) onSaveErrors(content, uri)
        errorsSaved = true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (report == null) {
            val view = LocalView.current
            DisposableEffect(Unit) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.done.toFloat() / progress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    color = TealPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.prog_running,
                        progress.done,
                        progress.total,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary,
                )
            } else {
                CircularProgressIndicator(color = TealPrimary)
            }
        } else {
            Text(
                text = stringResource(
                    R.string.rep_done,
                    report.seconds.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TealDark,
            )
            ValidationCount(stringResource(R.string.rep_added, report.added))
            ValidationCount(stringResource(R.string.rep_updated, report.updated))
            ValidationCount(stringResource(R.string.rep_skipped, report.skipped))
            ValidationCount(stringResource(R.string.rep_errors, report.errors))
            if (report.errors > 0 && report.errorCsv != null) {
                OutlinedButton(
                    onClick = { errorFileLauncher.launch("rxreader-errors.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rep_save_errors))
                }
                if (errorsSaved) {
                    Text(
                        text = stringResource(R.string.rep_errors_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = TealDark,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onNew,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.rep_new))
                }
                GradientButton(
                    text = stringResource(R.string.rep_done_button),
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Compact read-only dropdown for the wizard (mapping + format overrides). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    allLabel: String? = null,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected ?: allLabel ?: "",
            onValueChange = {},
            readOnly = true,
            label = if (label.isNotEmpty()) {
                { Text(label) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allLabel != null) {
                DropdownMenuItem(
                    text = { Text(allLabel, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun ContentResolver.displayName(uri: Uri): String {
    query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index) ?: "import"
        }
    }
    return "import"
}

@Suppress("UnusedPrivateProperty")
private val unusedTextButtonCheck = TextButton::class
