package com.almakhedhi.rxreader.feature.scan

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary
import com.almakhedhi.rxreader.core.scanner.QualityCheck
import kotlinx.coroutines.delay

/**
 * Runs the analysis pipeline for one image and shows progress, the
 * low-quality decision, or a retryable error. Navigates on via [onDone]
 * once the shared view-model holds the result.
 */
@Composable
fun AnalyzingScreen(
    imageUri: String,
    viewModel: ScanViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.analysisState.collectAsState()
    LaunchedEffect(imageUri) { viewModel.startAnalysis(imageUri) }
    LaunchedEffect(state) { if (state is AnalysisUiState.Done) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = stringResource(R.string.cd_scan_thumbnail),
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.analyzing_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = InkPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (val current = state) {
            AnalysisUiState.Idle,
            AnalysisUiState.Working,
            AnalysisUiState.Done,
                -> WorkingSteps()
            is AnalysisUiState.NeedsDecision -> QualityDecision(
                quality = current.quality,
                onRetake = onBack,
                onContinue = { viewModel.proceedDespiteWarning() },
            )
            is AnalysisUiState.Failed -> FailureView(
                message = current.message,
                onRetry = { viewModel.retry() },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun WorkingSteps() {
    val steps = listOf(
        R.string.analyzing_step_prepare,
        R.string.analyzing_step_read,
        R.string.analyzing_step_extract,
        R.string.analyzing_step_review,
    )
    var current by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(steps.size - 1) {
            delay(1800)
            current++
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, stepRes ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    index < current -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = TealPrimary,
                    )
                    index == current -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = TealPrimary,
                    )
                    else -> Icon(
                        imageVector = Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = InkMuted,
                    )
                }
                Text(
                    text = stringResource(stepRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index <= current) InkPrimary else InkMuted,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.analyzing_wait),
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
    )
}

@Composable
private fun QualityDecision(
    quality: QualityCheck.Quality,
    onRetake: () -> Unit,
    onContinue: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.quality_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (quality.isBlurry) {
                Text(
                    text = stringResource(R.string.quality_blurry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (quality.isDark) {
                Text(
                    text = stringResource(R.string.quality_dark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    GradientButton(
        text = stringResource(R.string.quality_continue),
        icon = Icons.AutoMirrored.Filled.ArrowForward,
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onRetake,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.quality_retake))
    }
}

@Composable
private fun FailureView(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    GradientButton(
        text = stringResource(R.string.analyzing_retry),
        icon = Icons.Filled.Refresh,
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onBack) {
        Text(text = stringResource(R.string.analyzing_back))
    }
}
