package com.almakhedhi.rxreader.feature.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var pendingTemplate by remember { mutableStateOf<ExportFormat?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeFor(state.format)),
    ) { uri: Uri? ->
        if (uri != null) viewModel.export(uri)
    }
    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val format = pendingTemplate
        pendingTemplate = null
        if (uri != null && format != null) viewModel.saveTemplate(format, uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.exp_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        text = stringResource(R.string.exp_scope),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealDark,
                    )
                    Text(
                        text = stringResource(R.string.exp_count, state.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary,
                    )
                    val all = stringResource(R.string.exp_all)
                    val filter = state.filter
                    val values = state.values
                    MappingDropdown(
                        label = stringResource(R.string.exp_f_therapeutic),
                        options = values?.therapeutics ?: emptyList(),
                        selected = filter.therapeutic,
                        allLabel = all,
                        onSelect = { viewModel.setFilter(filter.copy(therapeutic = it)) },
                    )
                    MappingDropdown(
                        label = stringResource(R.string.exp_f_form),
                        options = values?.forms ?: emptyList(),
                        selected = filter.form,
                        allLabel = all,
                        onSelect = { viewModel.setFilter(filter.copy(form = it)) },
                    )
                    MappingDropdown(
                        label = stringResource(R.string.exp_f_manufacturer),
                        options = values?.manufacturers ?: emptyList(),
                        selected = filter.manufacturer,
                        allLabel = all,
                        onSelect = { viewModel.setFilter(filter.copy(manufacturer = it)) },
                    )
                    FilterChip(
                        selected = filter.incompleteOnly,
                        onClick = {
                            viewModel.setFilter(filter.copy(incompleteOnly = !filter.incompleteOnly))
                        },
                        label = { Text(stringResource(R.string.exp_incomplete)) },
                    )
                }
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.exp_format),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealDark,
                    )
                    FormatOption(
                        label = stringResource(R.string.exp_csv),
                        selected = state.format == ExportFormat.CSV,
                        onClick = { viewModel.setFormat(ExportFormat.CSV) },
                    )
                    FormatOption(
                        label = stringResource(R.string.exp_xlsx),
                        selected = state.format == ExportFormat.XLSX,
                        onClick = { viewModel.setFormat(ExportFormat.XLSX) },
                    )
                    FormatOption(
                        label = stringResource(R.string.exp_json),
                        selected = state.format == ExportFormat.JSON,
                        onClick = { viewModel.setFormat(ExportFormat.JSON) },
                    )
                    FormatOption(
                        label = stringResource(R.string.exp_zip),
                        selected = state.format == ExportFormat.ZIP,
                        onClick = { viewModel.setFormat(ExportFormat.ZIP) },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (state.working) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = TealPrimary,
                        )
                        Text(
                            text = stringResource(R.string.exp_working),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    } else {
                        GradientButton(
                            text = stringResource(R.string.exp_start),
                            onClick = { exportLauncher.launch(suggestedName(state.format)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.done) {
                        Text(
                            text = stringResource(R.string.exp_done),
                            style = MaterialTheme.typography.bodySmall,
                            color = TealDark,
                        )
                    }
                }
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
                        text = stringResource(R.string.exp_templates),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealDark,
                    )
                    Text(
                        text = stringResource(R.string.exp_templates_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemplateButton(
                            label = "CSV",
                            onClick = {
                                pendingTemplate = ExportFormat.CSV
                                templateLauncher.launch("rxreader-template.csv")
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TemplateButton(
                            label = "XLSX",
                            onClick = {
                                pendingTemplate = ExportFormat.XLSX
                                templateLauncher.launch("rxreader-template.xlsx")
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TemplateButton(
                            label = "JSON",
                            onClick = {
                                pendingTemplate = ExportFormat.JSON
                                templateLauncher.launch("rxreader-template.json")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.templateSaved) {
                        Text(
                            text = stringResource(R.string.exp_template_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = TealDark,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkPrimary,
        )
    }
}

@Composable
private fun TemplateButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TealPrimary),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
}

private fun mimeFor(format: ExportFormat): String =
    when (format) {
        ExportFormat.CSV -> "text/csv"
        ExportFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ExportFormat.JSON -> "application/json"
        ExportFormat.ZIP -> "application/zip"
    }

private fun suggestedName(format: ExportFormat): String {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    val ext = when (format) {
        ExportFormat.CSV -> "csv"
        ExportFormat.XLSX -> "xlsx"
        ExportFormat.JSON -> "json"
        ExportFormat.ZIP -> "zip"
    }
    return "rxreader-drugs-$stamp.$ext"
}

