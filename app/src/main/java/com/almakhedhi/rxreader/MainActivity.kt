package com.almakhedhi.rxreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.almakhedhi.rxreader.core.auth.AuthRepository
import com.almakhedhi.rxreader.core.designsystem.theme.RxReaderTheme
import com.almakhedhi.rxreader.core.scanner.ScannerHelper
import com.almakhedhi.rxreader.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var scannerHelper: ScannerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Replace the splash theme (set in the manifest) with the normal theme.
        setTheme(R.style.Theme_RxReader)
        val sharedImage = extractSharedImage(intent)
        enableEdgeToEdge()
        setContent {
            RxReaderTheme {
                MainScreen(
                    authRepository = authRepository,
                    scannerHelper = scannerHelper,
                    sharedImageUri = sharedImage,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A new shared image while the app is open: restart with the new
        // intent so the share flow runs from a clean state.
        setIntent(intent)
        recreate()
    }

    /** Image URI when the app was opened via "share image" (else null). */
    private fun extractSharedImage(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return uri?.toString()
    }
}
