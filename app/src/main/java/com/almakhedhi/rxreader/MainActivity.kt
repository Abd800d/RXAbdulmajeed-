package com.almakhedhi.rxreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.almakhedhi.rxreader.core.designsystem.theme.RxReaderTheme
import com.almakhedhi.rxreader.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Replace the splash theme (set in the manifest) with the normal theme.
        setTheme(R.style.Theme_RxReader)
        enableEdgeToEdge()
        setContent {
            RxReaderTheme {
                MainScreen()
            }
        }
    }
}
