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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.almakhedhi.rxreader.BuildConfig
import com.almakhedhi.rxreader.R
import com.almakhedhi.rxreader.core.auth.AuthRepository
import com.almakhedhi.rxreader.core.scanner.ScannerHelper
import com.almakhedhi.rxreader.feature.auth.AuthRoute
import com.almakhedhi.rxreader.feature.drugs.DrugDetailScreen
import com.almakhedhi.rxreader.feature.drugs.DrugDetailViewModel
import com.almakhedhi.rxreader.feature.drugs.DrugEditorScreen
import com.almakhedhi.rxreader.feature.drugs.DrugEditorViewModel
import com.almakhedhi.rxreader.feature.drugs.DrugListScreen
import com.almakhedhi.rxreader.feature.drugs.DrugListViewModel
import com.almakhedhi.rxreader.feature.drugs.TrashScreen
import com.almakhedhi.rxreader.feature.drugs.TrashViewModel
import com.almakhedhi.rxreader.feature.history.HistoryDetailScreen
import com.almakhedhi.rxreader.feature.history.HistoryDetailViewModel
import com.almakhedhi.rxreader.feature.history.HistoryScreen
import com.almakhedhi.rxreader.feature.history.HistoryViewModel
import com.almakhedhi.rxreader.feature.home.HomeScreen
import com.almakhedhi.rxreader.feature.home.RecentRx
import com.almakhedhi.rxreader.feature.importexport.ExportScreen
import com.almakhedhi.rxreader.feature.importexport.ExportViewModel
import com.almakhedhi.rxreader.feature.importexport.ImportWizardScreen
import com.almakhedhi.rxreader.feature.importexport.ImportWizardViewModel
import com.almakhedhi.rxreader.feature.scan.AnalyzingScreen
import com.almakhedhi.rxreader.feature.scan.CameraScreen
import com.almakhedhi.rxreader.feature.scan.CropScreen
import com.almakhedhi.rxreader.feature.scan.ResultScreen
import com.almakhedhi.rxreader.feature.scan.ScanViewModel
import com.almakhedhi.rxreader.feature.search.BulkScreen
import com.almakhedhi.rxreader.feature.search.BulkViewModel
import com.almakhedhi.rxreader.feature.search.SearchScreen
import com.almakhedhi.rxreader.feature.search.SearchViewModel
import com.almakhedhi.rxreader.feature.settings.SettingsScreen
import com.almakhedhi.rxreader.feature.settings.SettingsViewModel
import com.almakhedhi.rxreader.feature.support.ContactScreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
private const val ROUTE_DRUGS = "drugs?query={query}"
private const val ROUTE_DRUG_DETAIL = "drug/{drugId}"
private const val ROUTE_DRUG_EDIT = "drugEdit?drugId={drugId}"
private const val ROUTE_TRASH = "drugsTrash"
private const val ROUTE_IMPORT = "import"
private const val ROUTE_EXPORT = "export"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_HISTORY_DETAIL = "history/{prescriptionId}"
private const val ROUTE_SEARCH = "search?query={query}"
private const val ROUTE_BULK = "bulk"
private const val ROUTE_SETTINGS = "settings"
private const val ARG_QUERY = "query"
private const val ARG_DRUG_ID = "drugId"
private const val ARG_PRESCRIPTION_ID = "prescriptionId"
private const val PREFS_SESSION = "session"
private const val PREF_GUEST = "guest"

/** Synchronous guest flag (SharedPreferences): offline entry without Firebase. */
private fun isGuestMode(context: android.content.Context): Boolean =
    context.getSharedPreferences(PREFS_SESSION, android.content.Context.MODE_PRIVATE)
        .getBoolean(PREF_GUEST, false)

private fun setGuestMode(context: android.content.Context, guest: Boolean) {
    context.getSharedPreferences(PREFS_SESSION, android.content.Context.MODE_PRIVATE)
        .edit().putBoolean(PREF_GUEST, guest).apply()
}

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
            startDestination = remember(cached) { if (cached == null && !isGuestMode(activity)) ROUTE_AUTH else ROUTE_HOME },
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_AUTH) {
                AuthRoute(
                    onContactClick = { navController.navigate(ROUTE_SUPPORT) },
                    onSkipClick = {
                        setGuestMode(activity, true)
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_AUTH) { inclusive = true }
                        }
                    },
                )
            }
            composable(ROUTE_HOME) {
                val homeViewModel: HomeViewModel = hiltViewModel()
                val recentSummaries by homeViewModel.recent.collectAsState()
                val linesTemplate = stringResource(R.string.recent_lines)
                HomeScreen(
                    onScanClick = launchScanner,
                    onGalleryClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(PickVisualMedia.ImageOnly),
                        )
                    },
                    onSearch = { query ->
                        navController.navigate("search?query=${Uri.encode(query)}")
                    },
                    onCatalogClick = { navController.navigate("drugs") },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    recent = recentSummaries.map { summary ->
                        RecentRx(
                            id = summary.id,
                            title = formatDateTime(summary.createdAt),
                            subtitle = listOfNotNull(
                                summary.doctor?.takeIf { it.isNotBlank() },
                                linesTemplate.format(summary.itemCount),
                            ).joinToString(" · "),
                        )
                    },
                    onRecentClick = { id -> navController.navigate("history/$id") },
                    onViewAllHistory = { navController.navigate(ROUTE_HISTORY) },
                    onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_SUPPORT) {
                ContactScreen(
                    appVersion = BuildConfig.VERSION_NAME,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_DRUGS,
                arguments = listOf(
                    navArgument(ARG_QUERY) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val viewModel: DrugListViewModel = hiltViewModel()
                DrugListScreen(
                    viewModel = viewModel,
                    initialQuery = Uri.decode(entry.arguments?.getString(ARG_QUERY) ?: ""),
                    onDrugClick = { id -> navController.navigate("drug/$id") },
                    onAddClick = { navController.navigate("drugEdit?drugId=-1") },
                    onImportClick = { navController.navigate(ROUTE_IMPORT) },
                    onExportClick = { navController.navigate(ROUTE_EXPORT) },
                    onTrashClick = { navController.navigate(ROUTE_TRASH) },
                    onBulkClick = { navController.navigate(ROUTE_BULK) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_DRUG_DETAIL,
                arguments = listOf(navArgument(ARG_DRUG_ID) { type = NavType.LongType }),
            ) { entry ->
                val viewModel: DrugDetailViewModel = hiltViewModel()
                val drugId = entry.arguments?.getLong(ARG_DRUG_ID) ?: -1L
                DrugDetailScreen(
                    viewModel = viewModel,
                    drugId = drugId,
                    onEdit = { navController.navigate("drugEdit?drugId=$drugId") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_DRUG_EDIT,
                arguments = listOf(
                    navArgument(ARG_DRUG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val viewModel: DrugEditorViewModel = hiltViewModel()
                DrugEditorScreen(
                    viewModel = viewModel,
                    drugId = entry.arguments?.getLong(ARG_DRUG_ID) ?: -1L,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_TRASH) {
                val viewModel: TrashViewModel = hiltViewModel()
                TrashScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_SEARCH,
                arguments = listOf(
                    navArgument(ARG_QUERY) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val viewModel: SearchViewModel = hiltViewModel()
                SearchScreen(
                    viewModel = viewModel,
                    initialQuery = Uri.decode(entry.arguments?.getString(ARG_QUERY) ?: ""),
                    onLocalClick = { id -> navController.navigate("drug/$id") },
                    onSaved = { id -> navController.navigate("drug/$id") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_BULK) {
                val viewModel: BulkViewModel = hiltViewModel()
                BulkScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_SETTINGS) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_IMPORT) {
                val wizardViewModel: ImportWizardViewModel = hiltViewModel()
                val exportViewModel: ExportViewModel = hiltViewModel()
                ImportWizardScreen(
                    viewModel = wizardViewModel,
                    exportViewModel = exportViewModel,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_EXPORT) {
                val viewModel: ExportViewModel = hiltViewModel()
                ExportScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_HISTORY) {
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onPrescriptionClick = { id -> navController.navigate("history/$id") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_HISTORY_DETAIL,
                arguments = listOf(navArgument(ARG_PRESCRIPTION_ID) { type = NavType.LongType }),
            ) { entry ->
                val viewModel: HistoryDetailViewModel = hiltViewModel()
                HistoryDetailScreen(
                    viewModel = viewModel,
                    prescriptionId = entry.arguments?.getLong(ARG_PRESCRIPTION_ID) ?: -1L,
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

private fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
