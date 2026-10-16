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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.almakhedhi.rxreader.core.data.model.DrugMatch
import com.almakhedhi.rxreader.core.data.model.MatchVia
import com.almakhedhi.rxreader.core.data.repo.OfficialDetails
import com.almakhedhi.rxreader.core.data.repo.OfficialMatch
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

/** Three search layers: on-device, official sources, AI (PLAN §4.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialQuery: String,
    onLocalClick: (Long) -> Unit,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.setQuery(initialQuery)
    }
    state.savedDrugId?.let { id ->
        LaunchedEffect(id) {
            onSaved(id)
            viewModel.consumeSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                )
            }

            // Layer 1: on-device.
            item {
                LayerHeader(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.layer_local),
                )
            }
            if (state.localLoading) {
                item { LoadingRow() }
            } else if (state.query.isNotBlank() && state.local.isEmpty()) {
                item { HintText(stringResource(R.string.local_empty)) }
            } else {
                items(state.local, key = { it.drug.id }) { match ->
                    LocalRow(match = match, onClick = { onLocalClick(match.drug.id) })
                }
            }

            // Layer 2: official sources.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LayerHeader(
                        icon = Icons.Filled.Verified,
                        title = stringResource(R.string.layer_official),
                        modifier = Modifier.weight(1f),
                    )
                    if (state.query.isNotBlank()) {
                        IconButton(
                            onClick = { GoogleSearch.launchQuery(context, state.query) },
                        ) {
                            Icon(
                                Icons.Filled.OpenInBrowser,
                                contentDescription = stringResource(R.string.google_search),
                                tint = TealPrimary,
                            )
                        }
                    }
                }
            }
            item { HintText(stringResource(R.string.official_hint)) }
            if (!state.officialFetched && !state.officialLoading) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadOfficial,
                        enabled = state.query.trim().length >= 2,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.official_load))
                    }
                }
            }
            if (state.officialLoading) {
                item { LoadingRow() }
            }
            if (state.officialFailed) {
                item { HintText(stringResource(R.string.error_network)) }
            } else if (state.officialFetched && state.official.isEmpty()) {
                item { HintText(stringResource(R.string.official_empty)) }
            } else {
                items(state.official, key = { it.rxcui ?: it.name }) { match ->
                    OfficialRow(
                        match = match,
                        selected = state.selection?.match == match,
                        onClick = { viewModel.selectOfficial(match) },
                    )
                }
            }
            state.selection?.let { selection ->
                item {
                    SelectionCard(
                        selection = selection,
                        saving = state.saving,
                        onClose = viewModel::clearSelection,
                        onSave = viewModel::saveOfficial,
                    )
                }
            }

            // Layer 3: AI suggestions.
            item {
                LayerHeader(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.layer_ai),
                )
            }
            item {
                val remaining = state.prefs?.aiRemaining
                OutlinedButton(
                    onClick = viewModel::askAi,
                    enabled = state.query.trim().length >= 2 && !state.aiLoading &&
                        (remaining == null || remaining > 0),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.ai_ask) +
                            (remaining?.let { " · " + stringResource(R.string.ai_remaining, it) } ?: ""),
                    )
                }
            }
            if (state.aiLoading) {
                item { LoadingRow() }
            }
            if (state.aiFailed || state.aiQuotaDenied) {
                item { HintText(stringResource(R.string.ai_empty)) }
            } else if (state.aiFetched && state.ai.isEmpty()) {
                item { HintText(stringResource(R.string.ai_empty)) }
            } else {
                items(state.ai, key = { it.name }) { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setQuery(suggestion.name)
                                viewModel.loadOfficial()
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(suggestion.name, fontWeight = FontWeight.Bold, color = InkPrimary)
                            if (suggestion.reason.isNotBlank()) {
                                Text(
                                    suggestion.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LayerHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = TealPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = InkMuted)
}

@Composable
private fun LocalRow(match: DrugMatch, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.drug.tradeEn?.takeIf { it.isNotBlank() }
                        ?: match.drug.tradeAr?.takeIf { it.isNotBlank() }
                        ?: match.drug.scientific ?: "",
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                match.drug.scientific?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
            SuggestionChip(onClick = onClick, label = { Text(viaLabel(match.via)) })
        }
    }
}

@Composable
private fun viaLabel(via: MatchVia): String =
    stringResource(
        when (via) {
            MatchVia.EXACT -> R.string.match_exact
            MatchVia.ALIAS -> R.string.match_alias
            MatchVia.FTS -> R.string.match_fts
            MatchVia.FUZZY -> R.string.match_fuzzy
        },
    )

@Composable
private fun OfficialRow(match: OfficialMatch, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TealPrimary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(match.name, fontWeight = FontWeight.Bold, color = InkPrimary)
            Text(
                match.sources.joinToString(" · ") +
                    (match.rxcui?.let { " · RxCUI $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }
}

@Composable
private fun SelectionCard(
    selection: OfficialSelection,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val details = selection.details
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selection.match.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
            when {
                selection.loading -> LoadingRow()
                selection.failed || details == null -> HintText(
                    stringResource(R.string.error_network),
                )
                else -> {
                    DetailField(R.string.f_generic, details.genericName)
                    DetailField(R.string.f_brands, details.brandNames.joinToString(", "))
                    DetailField(R.string.f_substance, details.substance)
                    DetailField(R.string.f_manufacturer, details.manufacturer)
                    DetailField(R.string.f_route, details.route)
                    DetailField(R.string.f_form, details.dosageForm)
                    DetailField(R.string.f_indications, details.indications?.take(500))
                    DetailField(R.string.f_dosage, details.dosage?.take(500))
                    DetailField(R.string.f_warnings, details.warnings?.take(500))
                    DetailField(R.string.f_packaging, details.packaging)
                    details.labelUrl?.let { url ->
                        OutlinedButton(
                            onClick = { GoogleSearch.launchUrl(context, url) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.label_open))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (selection.images.isNotEmpty()) {
                        Text(
                            stringResource(R.string.images_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selection.images, key = { it.url }) { image ->
                                AsyncImage(
                                    model = image.thumbUrl ?: image.url,
                                    contentDescription = image.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            GoogleSearch.launchUrl(context, image.url)
                                        },
                                )
                            }
                        }
                        selection.images.mapNotNull { it.license }.distinct().take(2)
                            .forEach { license ->
                                Text(
                                    stringResource(R.string.license_label, license),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                )
                            }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    GradientButton(
                        text = if (saving) stringResource(R.string.saving)
                        else stringResource(R.string.save_to_catalog),
                        onClick = onSave,
                        icon = Icons.Filled.Save,
                        modifier = Modifier.fillMaxWidth(),
                        loading = saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailField(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            stringResource(labelRes) + ": ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = InkMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = InkPrimary,
        )
    }
}
