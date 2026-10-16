package com.almakhedhi.rxreader.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.datastore.EnrichMode
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary

/** Bulk enrichment of incomplete drugs with estimate + live progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkScreen(viewModel: BulkViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val off = state.prefs.mode == EnrichMode.OFF

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bulk_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.bulk_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            if (state.loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.candidates.isEmpty()) {
                item { Text(stringResource(R.string.bulk_none)) }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.bulk_selected, state.selected.size, state.candidates.size),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = viewModel::selectAll) {
                            Text(stringResource(R.string.bulk_all))
                        }
                        TextButton(onClick = viewModel::selectNone) {
                            Text(stringResource(R.string.bulk_none_btn))
                        }
                    }
                }
                items(state.candidates, key = { it.id }) { drug ->
                    BulkRow(
                        drug = drug,
                        checked = drug.id in state.selected,
                        onToggle = { viewModel.toggle(drug.id) },
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setWithAi(!state.withAi) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.bulk_ai),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(
                                    R.string.bulk_ai_estimate,
                                    if (state.withAi) state.selected.size else 0,
                                    state.prefs.aiRemaining,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                        Switch(checked = state.withAi, onCheckedChange = viewModel::setWithAi)
                    }
                }
            }
            item {
                Text(
                    stringResource(
                        R.string.bulk_estimate,
                        state.selected.size,
                        state.prefs.aiRemaining,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            if (state.running) {
                item {
                    Column {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.bulk_running, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
            state.report?.let { report ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                R.string.bulk_done,
                                report.done,
                                report.enriched,
                                report.images,
                                report.aiDone,
                            ) + if (report.aiSkipped) "\n" + stringResource(R.string.bulk_ai_skipped) else "",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            if (state.failed) {
                item { Text(stringResource(R.string.error_network), color = InkMuted) }
            }
            item {
                GradientButton(
                    text = stringResource(R.string.bulk_start),
                    onClick = viewModel::askConfirm,
                    icon = Icons.Filled.CloudDownload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !off && !state.running && state.selected.isNotEmpty(),
                    loading = state.running,
                )
                if (off) {
                    Text(
                        stringResource(R.string.bulk_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            text = {
                Text(
                    stringResource(R.string.bulk_confirm, state.selected.size) +
                        if (state.withAi) {
                            "\n" + stringResource(
                                R.string.bulk_ai_estimate,
                                state.selected.size,
                                state.prefs.aiRemaining,
                            )
                        } else "",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::start) {
                    Text(stringResource(R.string.bulk_start))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelConfirm) {
                    Text(stringResource(R.string.bulk_cancel))
                }
            },
        )
    }
}

@Composable
private fun BulkRow(drug: DrugEntity, checked: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    drug.tradeEn?.takeIf { it.isNotBlank() }
                        ?: drug.tradeAr?.takeIf { it.isNotBlank() }
                        ?: drug.scientific ?: "",
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                Text(
                    missingSummary(drug),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
    }
}

@Composable
private fun missingSummary(drug: DrugEntity): String {
    val missing = mutableListOf<String>()
    if (drug.scientific.isNullOrBlank()) missing += stringResource(R.string.bulk_m_scientific)
    if (drug.manufacturer.isNullOrBlank()) missing += stringResource(R.string.bulk_m_manufacturer)
    if (drug.indications.isNullOrBlank()) missing += stringResource(R.string.bulk_m_indications)
    if (drug.dosageAdults.isNullOrBlank()) missing += stringResource(R.string.bulk_m_dosage)
    return stringResource(R.string.bulk_missing) + " " + missing.joinToString("، ")
}
