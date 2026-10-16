package com.almakhedhi.rxreader.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.datastore.EnrichMode
import com.almakhedhi.rxreader.core.datastore.MAX_AI_CAP
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val prefs = state.prefs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SectionTitle(stringResource(R.string.mode_title))
            ModeRow(
                selected = prefs.mode == EnrichMode.AUTO,
                title = stringResource(R.string.mode_auto),
                desc = stringResource(R.string.mode_auto_desc),
                onClick = { viewModel.setMode(EnrichMode.AUTO) },
            )
            ModeRow(
                selected = prefs.mode == EnrichMode.ASK,
                title = stringResource(R.string.mode_ask),
                desc = stringResource(R.string.mode_ask_desc),
                onClick = { viewModel.setMode(EnrichMode.ASK) },
            )
            ModeRow(
                selected = prefs.mode == EnrichMode.OFF,
                title = stringResource(R.string.mode_off),
                desc = stringResource(R.string.mode_off_desc),
                onClick = { viewModel.setMode(EnrichMode.OFF) },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setWifiOnly(!prefs.wifiOnly) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wifi_title),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.wifi_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    Switch(
                        checked = prefs.wifiOnly,
                        onCheckedChange = viewModel::setWifiOnly,
                    )
                }
            }

            SectionTitle(stringResource(R.string.cap_title))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cap_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.setCap((prefs.aiDailyCap - CAP_STEP).coerceAtLeast(0))
                            },
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }
                        Text(
                            prefs.aiDailyCap.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary,
                        )
                        IconButton(
                            onClick = {
                                viewModel.setCap(
                                    (prefs.aiDailyCap + CAP_STEP).coerceAtMost(MAX_AI_CAP),
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (prefs.aiDailyCap > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (prefs.aiUsedToday.toFloat() / prefs.aiDailyCap).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        stringResource(
                            R.string.cap_used,
                            prefs.aiUsedToday,
                            prefs.aiDailyCap,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }

            SectionTitle(stringResource(R.string.cache_title))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.cache_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::clearCache,
                        enabled = !state.clearing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.clearing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (state.cleared) stringResource(R.string.cache_cleared)
                            else stringResource(R.string.cache_clear),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ModeRow(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
    }
}

private const val CAP_STEP = 10
