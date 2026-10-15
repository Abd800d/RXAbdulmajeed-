package com.almakhedhi.rxreader.feature.history

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.database.entity.PrescriptionItemEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionWithItems
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.InkSecondary
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    prescriptionId: Long,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(prescriptionId) { viewModel.load(prescriptionId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val shareHeader = stringResource(R.string.share_header)
    val shareDoctor = stringResource(R.string.share_doctor)
    val shareItem = stringResource(R.string.share_item)
    val shareItemNoMatch = stringResource(R.string.share_item_nomatch)
    val shareWarnings = stringResource(R.string.share_warnings)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.det_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.hist_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val detail = state.detail ?: return@IconButton
                            val text = buildShareText(
                                detail = detail,
                                matchNames = state.matchNames,
                                header = shareHeader,
                                doctor = shareDoctor,
                                item = shareItem,
                                itemNoMatch = shareItemNoMatch,
                                warnings = shareWarnings,
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.det_share),
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.det_delete),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary)
                }
            }
            detail == null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = stringResource(R.string.det_not_found),
                        color = InkMuted,
                    )
                }
            }
            else -> {
                DetailBody(
                    detail = detail,
                    matchNames = state.matchNames,
                    imageBitmap = { name ->
                        val file = viewModel.imageFile(name)
                        val bitmap by produceState<ImageBitmap?>(null, name) {
                            value = withContext(Dispatchers.IO) {
                                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            }
                        }
                        bitmap
                    },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.hist_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.hist_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.hist_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailBody(
    detail: PrescriptionWithItems,
    matchNames: Map<Long, String>,
    imageBitmap: @Composable (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val date = remember(detail.prescription.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(detail.prescription.createdAt))
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                detail.prescription.doctor?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.det_doctor, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                    )
                }
                detail.prescription.clinic?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.det_clinic, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                    )
                }
                detail.prescription.dateStr?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.det_date, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                    )
                }
                detail.prescription.warnings.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.det_warnings),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        detail.prescription.imageFile?.let { name ->
            Text(
                text = stringResource(R.string.det_image),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            val bitmap = imageBitmap(name)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.det_image),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            text = stringResource(R.string.det_items, detail.items.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = InkPrimary,
        )
        detail.items.forEach { item ->
            ItemCard(item = item, matchName = item.matchedDrugId?.let { matchNames[it] })
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ItemCard(item: PrescriptionItemEntity, matchName: String?) {
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
                text = "${stringResource(R.string.det_written)}: ${item.written}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            if (matchName != null) {
                Text(
                    text = "${stringResource(R.string.det_match)}: $matchName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TealDark,
                )
            } else {
                Text(
                    text = stringResource(R.string.det_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            item.dose?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.det_dose, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            item.frequency?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.det_freq, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            item.duration?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.det_duration, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            val instructions = item.instructionsAr?.takeIf { it.isNotBlank() }
                ?: item.instructionsEn?.takeIf { it.isNotBlank() }
            instructions?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
        }
    }
}

/**
 * Share text is assembled from raw localized templates resolved by
 * the screen, so this builder needs no resource access.
 */
private fun buildShareText(
    detail: PrescriptionWithItems,
    matchNames: Map<Long, String>,
    header: String,
    doctor: String,
    item: String,
    itemNoMatch: String,
    warnings: String,
): String {
    val locale = Locale.getDefault()
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
        .format(Date(detail.prescription.createdAt))
    return buildString {
        appendLine(String.format(locale, header, date))
        detail.prescription.doctor?.takeIf { it.isNotBlank() }?.let {
            appendLine(String.format(locale, doctor, it))
        }
        detail.items.forEachIndexed { index, line ->
            val name = line.matchedDrugId?.let { matchNames[it] }
            if (name != null) {
                appendLine(String.format(locale, item, index + 1, name, line.written))
            } else {
                appendLine(String.format(locale, itemNoMatch, index + 1, line.written))
            }
        }
        detail.prescription.warnings.takeIf { it.isNotBlank() }?.let {
            appendLine(String.format(locale, warnings, it.replace("\n", " / ")))
        }
    }.trim()
}

@Suppress("UnusedPrivateProperty")
private val unusedOutlinedButtonCheck = OutlinedButton::class
