package com.almakhedhi.rxreader.core.scanner

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the ML Kit document scanner (Google Play services).
 * Needs no camera permission: scanning runs in a Play-services activity
 * whose result is returned to our IntentSender launcher.
 */
@Singleton
class ScannerHelper @Inject constructor() {

    private val options: GmsDocumentScannerOptions by lazy {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    /**
     * IntentSender that launches the scanner UI.
     * @throws Exception when Play services are missing or outdated — the
     * caller must fall back to the in-app camera in that case.
     */
    suspend fun getScanIntentSender(activity: Activity): IntentSender =
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity).await()
}
