package com.almakhedhi.rxreader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.almakhedhi.rxreader.feature.home.HomeScreen

/** App entry screen. Phase 0 hosts the home screen only. */
@Composable
fun MainScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        HomeScreen(modifier = Modifier.padding(innerPadding))
    }
}
