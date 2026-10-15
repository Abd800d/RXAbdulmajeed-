package com.almakhedhi.rxreader.feature.scan

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView

/**
 * Headless step: launches the crop UI once per image, then forwards the
 * cropped URI to analysis. Anything else (cancel, failure) goes back.
 */
@Composable
fun CropScreen(
    imageUri: String,
    onCropped: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var launched by rememberSaveable { mutableStateOf(false) }
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.toString()?.let(onCropped) ?: onCancel()
        } else {
            onCancel()
        }
    }
    LaunchedEffect(imageUri) {
        if (!launched) {
            launched = true
            cropLauncher.launch(
                CropImageContractOptions(
                    Uri.parse(imageUri),
                    CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
                        outputCompressFormat = Bitmap.CompressFormat.JPEG,
                        outputCompressQuality = 90,
                    ),
                ),
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = TealPrimary)
            Text(
                text = stringResource(R.string.crop_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
    }
}
