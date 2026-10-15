package com.almakhedhi.rxreader.feature.scan

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.ai.model.AnalyzedDrug
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.GoldAccent
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.InkSecondary
import com.almakhedhi.rxreader.core.designsystem.theme.TealContainer
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

/**
 * Pharmacist review of the AI reading: source image with the selected
 * medicine highlighted, editable drug cards, warnings, and confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ScanViewModel,
    onDone: () -> Unit,
) {
    val result by viewModel.result.collectAsState()
    val selected by viewModel.selectedDrug.collectAsState()
    val confirmed by viewModel.confirmed.collectAsState()
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val current = result
    if (current == null) {
        LaunchedEffect(Unit) { onDone() }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = TealPrimary)
        }
        return
    }
    if (confirmed) {
        ConfirmedView(onDone = onDone)
        return
    }
    val bitmap = remember { viewModel.sourceBitmap }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.result_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = TealContainer,
            ) {
                Text(
                    text = stringResource(
                        R.string.result_count,
                        current.medications.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = TealDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.result_review_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        current.meta.doctorName?.let { doctor ->
            Text(
                text = stringResource(R.string.result_doctor, doctor),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
            )
        }
        bitmap?.let {
            HighlightedImage(
                bitmap = it,
                box = current.medications.getOrNull(selected ?: -1)?.box2d,
            )
        }
        current.medications.forEachIndexed { index, drug ->
            DrugCard(
                drug = drug,
                index = index,
                isSelected = selected == index,
                onSelect = {
                    viewModel.selectDrug(if (selected == index) null else index)
                },
                onEdit = { editingIndex = index },
            )
        }
        if (current.warnings.isNotEmpty()) {
            WarningsCard(warnings = current.warnings)
        }
        GradientButton(
            text = stringResource(R.string.result_confirm),
            icon = Icons.Filled.Check,
            onClick = { viewModel.confirmAll() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    editingIndex?.let { index ->
        current.medications.getOrNull(index)?.let { drug ->
            EditDrugSheet(
                drug = drug,
                onSave = {
                    viewModel.updateDrug(index, it)
                    editingIndex = null
                },
                onDismiss = { editingIndex = null },
            )
        }
    }
}

@Composable
private fun HighlightedImage(
    bitmap: Bitmap,
    box: List<Int>?,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    bitmap.width.toFloat() /
                        bitmap.height.coerceAtLeast(1),
                ),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_source_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            val valid = box?.takeIf { it.size == 4 }
            if (valid != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val (ymin, xmin, ymax, xmax) = valid
                    val left = xmin / 1000f * size.width
                    val top = ymin / 1000f * size.height
                    val width = (xmax - xmin).coerceAtLeast(1) / 1000f * size.width
                    val height = (ymax - ymin).coerceAtLeast(1) / 1000f * size.height
                    drawRect(
                        color = GoldAccent.copy(alpha = 0.30f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                    )
                    drawRect(
                        color = GoldAccent,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrugCard(
    drug: AnalyzedDrug,
    index: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = TealPrimary,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 8.dp),
                ) {
                    Text(
                        text = drug.tradeName ?: drug.writtenText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                    )
                    drug.scientificName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                        )
                    }
                }
                ConfidenceChip(confidence = drug.confidence)
            }
            if (drug.illegible) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = stringResource(R.string.result_illegible),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                drug.strength?.let { InfoChip(label = it) }
                InfoChip(label = stringResource(formLabel(drug.dosageForm)))
            }
            listOfNotNull(drug.dose, drug.frequency, drug.duration)
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary,
                    )
                }
            drug.route?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            Text(
                text = stringResource(R.string.result_instructions),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = InkSecondary,
            )
            Text(
                text = drug.instructionsAr.ifBlank { drug.instructionsEn },
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary,
            )
            if (drug.alternatives.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.result_alternatives),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary,
                )
                drug.alternatives.take(3).forEach { alternative ->
                    Text(
                        text = "• ${alternative.tradeName} " +
                            "(${(alternative.confidence * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            drug.notes?.let {
                Text(
                    text = stringResource(R.string.result_notes) + " " + it,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            Text(
                text = "«${drug.writtenText}»",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.HighlightAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(
                            if (isSelected) {
                                R.string.result_deselect
                            } else {
                                R.string.result_select
                            },
                        ),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.result_edit),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: Double) {
    val color = when {
        confidence >= 0.8 -> Color(0xFF2E7D32)
        confidence >= 0.5 -> Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = CircleShape,
        color = color,
    ) {
        Text(
            text = stringResource(
                R.string.result_confidence,
                (confidence * 100).toInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun InfoChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TealContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TealDark,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.result_warnings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDrugSheet(
    drug: AnalyzedDrug,
    onSave: (AnalyzedDrug) -> Unit,
    onDismiss: () -> Unit,
) {
    var trade by remember(drug) { mutableStateOf(drug.tradeName ?: "") }
    var scientific by remember(drug) { mutableStateOf(drug.scientificName ?: "") }
    var strength by remember(drug) { mutableStateOf(drug.strength ?: "") }
    var dose by remember(drug) { mutableStateOf(drug.dose ?: "") }
    var frequency by remember(drug) { mutableStateOf(drug.frequency ?: "") }
    var duration by remember(drug) { mutableStateOf(drug.duration ?: "") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            SheetField(value = trade, onChange = { trade = it }, label = R.string.edit_trade)
            SheetField(
                value = scientific,
                onChange = { scientific = it },
                label = R.string.edit_scientific,
            )
            SheetField(
                value = strength,
                onChange = { strength = it },
                label = R.string.edit_strength,
            )
            SheetField(value = dose, onChange = { dose = it }, label = R.string.edit_dose)
            SheetField(
                value = frequency,
                onChange = { frequency = it },
                label = R.string.edit_frequency,
            )
            SheetField(
                value = duration,
                onChange = { duration = it },
                label = R.string.edit_duration,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    text = stringResource(R.string.edit_save),
                    icon = Icons.Filled.Check,
                    onClick = {
                        onSave(
                            drug.copy(
                                tradeName = trade.ifBlank { null },
                                scientificName = scientific.ifBlank { null },
                                strength = strength.ifBlank { null },
                                dose = dose.ifBlank { null },
                                frequency = frequency.ifBlank { null },
                                duration = duration.ifBlank { null },
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.edit_cancel))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetField(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(label)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ConfirmedView(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.result_confirmed_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = InkPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.result_confirmed_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        GradientButton(
            text = stringResource(R.string.result_done),
            icon = Icons.Filled.Check,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formLabel(form: String): Int =
    when (form.lowercase()) {
        "tablet" -> R.string.form_tablet
        "capsule" -> R.string.form_capsule
        "syrup" -> R.string.form_syrup
        "suspension" -> R.string.form_suspension
        "injection" -> R.string.form_injection
        "drops" -> R.string.form_drops
        "cream" -> R.string.form_cream
        "ointment" -> R.string.form_ointment
        "suppository" -> R.string.form_suppository
        "inhaler" -> R.string.form_inhaler
        "sachet" -> R.string.form_sachet
        else -> R.string.form_other
    }
