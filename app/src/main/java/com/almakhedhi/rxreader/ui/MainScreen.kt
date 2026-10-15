package com.almakhedhi.rxreader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.almakhedhi.rxreader.BuildConfig
import com.almakhedhi.rxreader.core.auth.AuthRepository
import com.almakhedhi.rxreader.feature.auth.AuthRoute
import com.almakhedhi.rxreader.feature.home.HomeScreen
import com.almakhedhi.rxreader.feature.support.ContactScreen

/**
 * App entry screen. Phase 1: a simple auth gate (login when signed out,
 * home when signed in) plus the contact screen. A full navigation graph
 * arrives in Phase 2 when more destinations exist.
 */
@Composable
fun MainScreen(authRepository: AuthRepository) {
    val cached = remember { authRepository.cachedUser }
    val user by authRepository.currentUser.collectAsState(initial = cached)
    var showSupport by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                showSupport -> ContactScreen(
                    appVersion = BuildConfig.VERSION_NAME,
                    onBack = { showSupport = false },
                )

                user == null -> AuthRoute(
                    onContactClick = { showSupport = true },
                )

                else -> HomeScreen()
            }
        }
    }
}
