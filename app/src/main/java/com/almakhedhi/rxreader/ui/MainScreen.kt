package com.almakhedhi.rxreader.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.almakhedhi.rxreader.BuildConfig
import com.almakhedhi.rxreader.core.auth.AuthRepository
import com.almakhedhi.rxreader.core.scanner.ScannerHelper
import com.almakhedhi.rxreader.feature.auth.AuthRoute
import com.almakhedhi.rxreader.feature.home.HomeScreen
import com.almakhedhi.rxreader.feature.scan.AnalyzingScreen
import com.almakhedhi.rxreader.feature.scan.CameraScreen
import com.almakhedhi.rxreader.feature.scan.CropScreen
import com.almakhedhi.rxreader.feature.scan.ResultScreen
import com.almakhedhi.rxreader.feature.scan.ScanViewModel
import com.almakhedhi.rxreader.feature.support.ContactScreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

private const val ROUTE_AUTH = "auth"
private const val ROUTE_HOME = "home"
private const val ROUTE_SUPPORT = "support"
private const val ROUTE_SCAN_GRAPH = "scan_graph"
private const val ROUTE_ANALYZING = "analyzing/{imageUri}"
private const val ROUTE_CROP = "crop/{imageUri}"
private const val ROUTE_CAMERA = "camera"
private const val ROUTE_RESULT = "result"
private const val ARG_IMAGE_URI = "imageUri"

/**
 * App entry screen: auth gate + navigation graph. Owns the document-scanner
 * and gallery launchers so feature screens stay navigation-agnostic.
 */
@Composable
fun MainScreen(
    authRepository: AuthRepository,
    scannerHelper: ScannerHelper,
    sharedImageUri: String?,
) {
    val navController = rememberNavController()
    val cached = remember { authRepository.cachedUser }
    val user by authRepository.currentUser.collectAsState(initial = cached)
    var pendingShare by rememberSaveable(sharedImageUri) { mutableStateOf(sharedImageUri) }
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()

    val gotoAnalyzing: (String) -> Unit = { uri ->
        navController.navigate("analyzing/${Uri.encode(uri)}")
    }
    val scannerLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri?.toString()?.let(gotoAnalyzing)
        }
    }
    val pickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.toString()?.let { navController.navigate("crop/${Uri.encode(it)}") }
    }
    val launchScanner: () -> Unit = {
        scope.launch {
            try {
                val sender = scannerHelper.getScanIntentSender(activity)
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (_: Exception) {
                // No (usable) Play services: fall back to the in-app camera.
                navController.navigate(ROUTE_CAMERA)
            }
        }
    }

    // After sign-in, leave the auth screen for home.
    LaunchedEffect(user) {
        if (user != null && navController.currentDestination?.route == ROUTE_AUTH) {
            navController.navigate(ROUTE_HOME) {
                popUpTo(ROUTE_AUTH) { inclusive = true }
            }
        }
    }
    // A shared image waits for sign-in, then opens analysis directly.
    LaunchedEffect(user, pendingShare) {
        val share = pendingShare
        if (user != null && share != null) {
            pendingShare = null
            gotoAnalyzing(share)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = remember(cached) { if (cached == null) ROUTE_AUTH else ROUTE_HOME },
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_AUTH) {
                AuthRoute(onContactClick = { navController.navigate(ROUTE_SUPPORT) })
            }
            composable(ROUTE_HOME) {
                HomeScreen(
                    onScanClick = launchScanner,
                    onGalleryClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
            composable(ROUTE_SUPPORT) {
                ContactScreen(
                    appVersion = BuildConfig.VERSION_NAME,
                    onBack = { navController.popBackStack() },
                )
            }
            navigation(startDestination = ROUTE_ANALYZING, route = ROUTE_SCAN_GRAPH) {
                composable(
                    route = ROUTE_ANALYZING,
                    arguments = listOf(navArgument(ARG_IMAGE_URI) { type = NavType.StringType }),
                ) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(ROUTE_SCAN_GRAPH)
                    }
                    val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                    AnalyzingScreen(
                        imageUri = Uri.decode(entry.arguments?.getString(ARG_IMAGE_URI) ?: ""),
                        viewModel = viewModel,
                        onDone = {
                            navController.navigate(ROUTE_RESULT) {
                                popUpTo(ROUTE_ANALYZING) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = ROUTE_CROP,
                    arguments = listOf(navArgument(ARG_IMAGE_URI) { type = NavType.StringType }),
                ) { entry ->
                    CropScreen(
                        imageUri = Uri.decode(entry.arguments?.getString(ARG_IMAGE_URI) ?: ""),
                        onCropped = { cropped ->
                            navController.navigate("analyzing/${Uri.encode(cropped)}") {
                                popUpTo(ROUTE_CROP) { inclusive = true }
                            }
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_CAMERA) {
                    CameraScreen(
                        onCaptured = { captured ->
                            navController.navigate("analyzing/${Uri.encode(captured)}") {
                                popUpTo(ROUTE_CAMERA) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_RESULT) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(ROUTE_SCAN_GRAPH)
                    }
                    val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                    ResultScreen(
                        viewModel = viewModel,
                        onDone = { navController.popBackStack(ROUTE_HOME, false) },
                    )
                }
            }
        }
    }
}
