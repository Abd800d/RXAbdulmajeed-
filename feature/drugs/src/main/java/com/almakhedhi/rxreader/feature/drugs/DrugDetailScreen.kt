package com.almakhedhi.rxreader.feature.drugs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.almakhedhi.rxreader.core.data.repo.EnrichImage
import com.almakhedhi.rxreader.core.database.entity.DrugImageEntity
import com.almakhedhi.rxreader.core.datastore.EnrichMode
import java.io.File
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.InkSecondary
import com.almakhedhi.rxreader.core.designsystem.theme.TealContainer
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugDetailScreen(
    viewModel: DrugDetailViewModel,
    drugId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var showEnrichConfirm by remember { mutableStateOf(false) }
    var showAiConfirm by remember { mutableStateOf(false) }
    var showWeb by remember { mutableStateOf(false) }
    val pickLauncher = rememberLauncherForActivityResult(
        PickMultipleVisualMedia(DrugDetailViewModel.MAX_ATTACH),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachUris(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(TakePicturePreview()) { bitmap ->
        if (bitmap != null) viewModel.attachBitmap(bitmap)
    }
    LaunchedEffect(drugId) { viewModel.load(drugId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.drugs_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.detail_edit),
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.detail_delete),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val drug = state.drug
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary)
                }
            }
            drug == null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = stringResource(R.string.detail_not_found),
                        color = InkMuted,
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = drugDisplayName(drug),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (drug.reviewed) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.detail_reviewed_yes),
                                tint = TealPrimary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFB7791F),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.needs_review),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB7791F),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                    Section(
                        title = stringResource(R.string.detail_section_names),
                        rows = listOf(
                            R.string.fld_trade_en to drug.tradeEn,
                            R.string.fld_trade_ar to drug.tradeAr,
                            R.string.fld_scientific to drug.scientific,
                            R.string.fld_actives to drug.actives,
                            R.string.fld_strength to drug.strength,
                            R.string.fld_form to drug.dosageForm?.let { formLabel(it) },
                            R.string.fld_route to drug.route,
                        ),
                    )
                    Section(
                        title = stringResource(R.string.detail_section_product),
                        rows = listOf(
                            R.string.fld_manufacturer to drug.manufacturer,
                            R.string.fld_country to drug.country,
                            R.string.fld_agent to drug.agent,
                            R.string.fld_reg_no to drug.regNo,
                            R.string.fld_barcode to drug.barcode,
                            R.string.fld_pack to drug.packSize,
                            R.string.fld_price to drug.price?.let {
                                stringResource(R.string.drugs_price_yer, formatPrice(it))
                            },
                            R.string.fld_price_date to drug.priceDate,
                            R.string.fld_dispensing to dispensingLabel(drug.dispensing),
                        ),
                    )
                    Section(
                        title = stringResource(R.string.detail_section_class),
                        rows = listOf(
                            R.string.fld_therapeutic to drug.therapeutic,
                            R.string.fld_atc to drug.atc,
                        ),
                    )
                    Section(
                        title = stringResource(R.string.detail_section_clinical),
                        rows = listOf(
                            R.string.fld_indications to drug.indications,
                            R.string.fld_dose_adults to drug.dosageAdults,
                            R.string.fld_dose_children to drug.dosageChildren,
                            R.string.fld_contra to drug.contraindications,
                            R.string.fld_side to drug.sideEffects,
                            R.string.fld_interactions to drug.interactions,
                            R.string.fld_pregnancy to drug.pregnancy,
                            R.string.fld_warnings to drug.warnings,
                            R.string.fld_storage to drug.storage,
                        ),
                    )
                    Section(
                        title = stringResource(R.string.detail_section_system),
                        rows = listOf(
                            R.string.fld_rxnorm to drug.rxNormId,
                            R.string.fld_dailymed to drug.dailymedId,
                            R.string.fld_source to drug.source,
                            R.string.fld_reviewed to stringResource(
                                if (drug.reviewed) {
                                    R.string.detail_reviewed_yes
                                } else {
                                    R.string.detail_reviewed_no
                                },
                            ),
                        ),
                    )
                    AliasSection(
                        aliases = state.aliases.map { it.aliasRaw to it.id },
                        onAdd = { showAliasDialog = true },
                        onRemove = { viewModel.removeAlias(it) },
                    )
                    EnrichSection(
                        state = state,
                        onEnrichClick = {
                            if (state.prefs.mode == EnrichMode.ASK) showEnrichConfirm = true
                            else viewModel.enrich()
                        },
                        onAiClick = { showAiConfirm = true },
                    )
                    GallerySection(
                        images = state.images,
                        fileFor = viewModel::imageFile,
                        onPick = {
                            pickLauncher.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(PickVisualMedia.ImageOnly)
                                    .build(),
                            )
                        },
                        onCamera = { cameraLauncher.launch(null) },
                        onWeb = {
                            showWeb = true
                            viewModel.loadWebImages()
                        },
                        onDelete = viewModel::deleteImage,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.detail_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.detail_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }
    if (showAliasDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text(stringResource(R.string.detail_alias_add)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.detail_alias_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addAlias(text)
                        showAliasDialog = false
                    },
                ) { Text(stringResource(R.string.detail_alias_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }
    if (showEnrichConfirm) {
        AlertDialog(
            onDismissRequest = { showEnrichConfirm = false },
            text = { Text(stringResource(R.string.enrich_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEnrichConfirm = false
                        viewModel.enrich()
                    },
                ) { Text(stringResource(R.string.enrich_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showEnrichConfirm = false }) {
                    Text(stringResource(R.string.enrich_no))
                }
            },
        )
    }
    if (showAiConfirm) {
        AlertDialog(
            onDismissRequest = { showAiConfirm = false },
            text = {
                Text(
                    stringResource(
                        R.string.ai_confirm,
                        state.prefs.aiRemaining,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAiConfirm = false
                        viewModel.aiComplete()
                    },
                ) { Text(stringResource(R.string.enrich_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showAiConfirm = false }) {
                    Text(stringResource(R.string.enrich_no))
                }
            },
        )
    }
    state.enrichReport?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::consumeEnrichReport,
            text = {
                Text(
                    stringResource(
                        R.string.enrich_done,
                        stringResource(
                            if (report.enriched) R.string.enrich_yes else R.string.enrich_no,
                        ),
                        report.images,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::consumeEnrichReport) {
                    Text(stringResource(R.string.detail_confirm))
                }
            },
        )
    }
    if (state.enrichFailed) {
        AlertDialog(
            onDismissRequest = viewModel::consumeEnrichReport,
            text = { Text(stringResource(R.string.enrich_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeEnrichReport) {
                    Text(stringResource(R.string.detail_confirm))
                }
            },
        )
    }
    if (state.aiRunning) {
        AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = TealPrimary,
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = stringResource(R.string.ai_running),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            },
            confirmButton = {},
        )
    }
    state.aiReport?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::consumeAiReport,
            text = {
                Text(
                    when {
                        report.quotaDenied -> stringResource(R.string.ai_quota)
                        report.filled.isEmpty() -> stringResource(R.string.ai_none)
                        else -> stringResource(
                            R.string.ai_filled,
                            report.filled.joinToString(", "),
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::consumeAiReport) {
                    Text(stringResource(R.string.detail_confirm))
                }
            },
        )
    }
    if (state.aiFailed) {
        AlertDialog(
            onDismissRequest = viewModel::consumeAiReport,
            text = { Text(stringResource(R.string.ai_error)) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeAiReport) {
                    Text(stringResource(R.string.detail_confirm))
                }
            },
        )
    }
    if (showWeb) {
        WebImagesDialog(
            images = state.webImages,
            loading = state.webLoading,
            downloading = state.downloading,
            onDownload = viewModel::downloadWebImage,
            onDismiss = {
                showWeb = false
                viewModel.clearWebImages()
            },
        )
    }
}

@Composable
private fun Section(title: String, rows: List<Pair<Int, String?>>) {
    val visible = rows.filter { !it.second.isNullOrBlank() }
    if (visible.isEmpty()) return
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TealDark,
            )
            Spacer(modifier = Modifier.height(8.dp))
            visible.forEach { (labelRes, value) ->
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                )
                Text(
                    text = value ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasSection(
    aliases: List<Pair<String, Long>>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_aliases),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TealDark,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.detail_alias_add),
                        tint = TealPrimary,
                    )
                }
            }
            if (aliases.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    aliases.forEach { (raw, id) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TealContainer,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                            ) {
                                Text(
                                    text = raw,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealDark,
                                )
                                IconButton(
                                    onClick = { onRemove(id) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = InkSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPrice(price: Double): String =
    if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()

@Composable
private fun EnrichSection(
    state: DrugDetailUiState,
    onEnrichClick: () -> Unit,
    onAiClick: () -> Unit,
) {
    val off = state.prefs.mode == EnrichMode.OFF
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.enrich_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TealDark,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.enriching) {
                LinearProgressIndicator(
                    progress = { state.enrichProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.enrich_running,
                        (state.enrichProgress * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEnrichClick,
                        enabled = !off,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.enrich_auto),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onAiClick,
                        enabled = !off && !state.aiRunning && state.prefs.aiRemaining > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.enrich_ai),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (off) {
                    Text(
                        text = stringResource(R.string.enrich_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun GallerySection(
    images: List<DrugImageEntity>,
    fileFor: (String) -> File,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onWeb: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.gallery_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TealDark,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (images.isEmpty()) {
                Text(
                    text = stringResource(R.string.gallery_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.id }) { image ->
                        Box {
                            AsyncImage(
                                model = image.fileName?.let(fileFor),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            IconButton(
                                onClick = { onDelete(image.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(
                                        R.string.gallery_delete_cd,
                                    ),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.gallery_pick),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                OutlinedButton(
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.gallery_camera),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                OutlinedButton(
                    onClick = onWeb,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.gallery_web),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WebImagesDialog(
    images: List<EnrichImage>,
    loading: Boolean,
    downloading: Set<String>,
    onDownload: (EnrichImage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_title)) },
        text = {
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = TealPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                images.isEmpty() -> Text(stringResource(R.string.web_empty))
                else -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(images, key = { it.url }) { image ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = image.thumbUrl ?: image.url,
                                    contentDescription = image.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = image.label ?: image.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = image.license ?: image.credit ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkMuted,
                                        maxLines = 1,
                                    )
                                }
                                val busy = image.url in downloading
                                TextButton(
                                    onClick = { onDownload(image) },
                                    enabled = !busy,
                                ) {
                                    Text(
                                        stringResource(
                                            if (busy) R.string.web_downloading
                                            else R.string.web_download,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_cancel))
            }
        },
    )
}
