package com.almakhedhi.rxreader.feature.drugs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import com.almakhedhi.rxreader.core.database.dao.DrugSort
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.InkSecondary
import com.almakhedhi.rxreader.core.designsystem.theme.TealContainer
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugListScreen(
    viewModel: DrugListViewModel,
    initialQuery: String,
    onDrugClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onTrashClick: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var text by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) viewModel.setQuery(initialQuery)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.drugs_title),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.drugs_count, state.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.drugs_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportClick) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = stringResource(R.string.drugs_import),
                        )
                    }
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.drugs_export),
                        )
                    }
                    IconButton(onClick = onTrashClick) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.drugs_trash),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = TealPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.drugs_add),
                    tint = Color.White,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.drugs_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                text = ""
                                viewModel.setQuery("")
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilterCard(
                filter = state.filter,
                values = state.values,
                onFilter = { viewModel.setFilter(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.loading && state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TealPrimary)
                }
            } else if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (state.searching) {
                                R.string.drugs_search_empty
                            } else {
                                R.string.drugs_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { drug ->
                        DrugRow(drug = drug, onClick = { onDrugClick(drug.id) })
                    }
                    if (state.hasMore) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.drugs_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCard(
    filter: DrugFilter,
    values: DrugRepository.FilterValues?,
    onFilter: (DrugFilter) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val activeCount = listOfNotNull(
        filter.therapeutic,
        filter.form,
        filter.manufacturer,
        filter.reviewed?.toString(),
    ).size + if (filter.incompleteOnly) 1 else 0
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = TealPrimary,
                )
                Text(
                    text = stringResource(R.string.drugs_filters),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                if (activeCount > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = TealContainer) {
                        Text(
                            text = "$activeCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = TealDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                val all = stringResource(R.string.drugs_all)
                OptionDropdown(
                    label = stringResource(R.string.fld_therapeutic),
                    options = values?.therapeutics ?: emptyList(),
                    selected = filter.therapeutic,
                    allLabel = all,
                    onSelect = { onFilter(filter.copy(therapeutic = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OptionDropdown(
                    label = stringResource(R.string.fld_form),
                    options = values?.forms ?: emptyList(),
                    selected = filter.form,
                    allLabel = all,
                    onSelect = { onFilter(filter.copy(form = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OptionDropdown(
                    label = stringResource(R.string.fld_manufacturer),
                    options = values?.manufacturers ?: emptyList(),
                    selected = filter.manufacturer,
                    allLabel = all,
                    onSelect = { onFilter(filter.copy(manufacturer = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.reviewed == true,
                        onClick = {
                            onFilter(filter.copy(reviewed = if (filter.reviewed == true) null else true))
                        },
                        label = { Text(stringResource(R.string.drugs_reviewed)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = filter.incompleteOnly,
                        onClick = { onFilter(filter.copy(incompleteOnly = !filter.incompleteOnly)) },
                        label = { Text(stringResource(R.string.drugs_incomplete)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val sortOptions = listOf(
                    stringResource(R.string.drugs_sort_name),
                    stringResource(R.string.drugs_sort_updated),
                    stringResource(R.string.drugs_sort_created),
                )
                val sorts = listOf(DrugSort.NAME, DrugSort.UPDATED_DESC, DrugSort.CREATED_DESC)
                OptionDropdown(
                    label = stringResource(R.string.drugs_sort),
                    options = sortOptions,
                    selected = sortOptions[sorts.indexOf(filter.sort)],
                    onSelect = { label ->
                        label?.let {
                            onFilter(filter.copy(sort = sorts[sortOptions.indexOf(it)]))
                        }
                    },
                )
                if (activeCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onFilter(DrugFilter(sort = filter.sort)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.drugs_clear_filters))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugRow(drug: DrugEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drugDisplayName(drug),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                listOfNotNull(
                    drug.scientific?.takeIf { it.isNotBlank() },
                    drug.strength?.takeIf { it.isNotBlank() },
                ).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    drug.dosageForm?.let {
                        MiniChip(formLabel(it))
                    }
                    drug.price?.let {
                        MiniChip(stringResource(R.string.drugs_price_yer, formatPrice(it)))
                    }
                }
            }
            if (drug.reviewed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.drugs_reviewed),
                    tint = TealPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = TealContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TealDark,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatPrice(price: Double): String =
    if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()
