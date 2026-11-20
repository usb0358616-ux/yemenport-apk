package com.example.yemenport

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yemenport.core.BiometricAuthentication
import com.example.yemenport.core.configureAppCheck
import com.example.yemenport.data.local.AppDatabase
import com.example.yemenport.data.remote.FirestoreSyncRepository
import com.example.yemenport.data.repository.SovereignRepository
import com.example.yemenport.domain.ai.SovereignAiRepository
import com.example.yemenport.ui.SovereignViewModel
import com.example.yemenport.ui.UiState
import com.example.yemenport.ui.auth.AuthScreen
import com.example.yemenport.ui.screens.*
import com.example.yemenport.ui.theme.YemenPortTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard),
    SCANNER("الماسح", Icons.Default.DocumentScanner),
    TRAVELERS("المسافرون", Icons.Default.People),
    IMPORT("الاستيراد", Icons.Default.UploadFile),
    MODULES("الوحدات", Icons.Default.Apps),
    SETTINGS("الإعدادات", Icons.Default.Settings)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModel, or Firebase call.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            var isDarkOverride by remember { mutableStateOf<Boolean?>(null) }
            YemenPortTheme(darkTheme = isDarkOverride ?: false) {
                AppNavigation(
                    onDarkModeChanged = { isDarkOverride = it }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    onDarkModeChanged: (Boolean) -> Unit
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen()
    } else {
        key(user.uid) {
            AuthenticatedBorderApp(
                currentUserId = user.uid,
                onDarkModeChanged = onDarkModeChanged,
                onSignOut = { auth.signOut() }
            )
        }
    }
}

@Composable
private fun AuthenticatedBorderApp(
    currentUserId: String,
    onDarkModeChanged: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: SovereignViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val fsDb = FirebaseFirestore.getInstance(dbId)
                val localDb = AppDatabase.getInstance(app)
                val repo = SovereignRepository(
                    dao = localDb.borderDao(),
                    firestoreSync = FirestoreSyncRepository(fsDb)
                )
                SovereignViewModel(
                    repository = repo,
                    aiRepository = SovereignAiRepository(),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext
                )
            }
        }
    )

    val session by viewModel.session.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudProfileState.collectAsStateWithLifecycle()
    val kpis by viewModel.kpis.collectAsStateWithLifecycle()
    val travelers by viewModel.travelers.collectAsStateWithLifecycle()
    val travels by viewModel.travels.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val seizures by viewModel.seizures.collectAsStateWithLifecycle()
    val migrants by viewModel.migrants.collectAsStateWithLifecycle()
    val refugees by viewModel.refugees.collectAsStateWithLifecycle()
    val foreigners by viewModel.foreigners.collectAsStateWithLifecycle()
    val permits by viewModel.permits.collectAsStateWithLifecycle()
    val citizenshipCases by viewModel.citizenshipCases.collectAsStateWithLifecycle()
    val agencies by viewModel.agencies.collectAsStateWithLifecycle()
    val correspondence by viewModel.correspondence.collectAsStateWithLifecycle()
    val mofaVisas by viewModel.mofaVisas.collectAsStateWithLifecycle()
    val officers by viewModel.officers.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    val criticalAlert by viewModel.criticalAlert.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val scannedMrz by viewModel.scannedMrz.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    val stagedFiles by viewModel.stagedFiles.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val stagedBatch by viewModel.stagedBatch.collectAsStateWithLifecycle()
    val lastCommitResult by viewModel.lastCommitResult.collectAsStateWithLifecycle()
    val isBiometricLocked by viewModel.isBiometricLocked.collectAsStateWithLifecycle()

    val biometricAuth = remember { BiometricAuthentication() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, session.biometricEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkIdleTimeout()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(session.biometricEnabled, isBiometricLocked) {
        if (session.biometricEnabled && !isBiometricLocked) {
            while (isActive) {
                delay(15_000L)
                viewModel.checkIdleTimeout()
            }
        }
    }

    LaunchedEffect(session.highContrastMode) {
        onDarkModeChanged(session.highContrastMode)
    }

    if (session.biometricEnabled && isBiometricLocked) {
        BiometricLockScreen(
            officerName = session.officerName,
            portName = session.assignedPort,
            biometricAuth = biometricAuth,
            onUnlocked = { viewModel.unlockSession() },
            onSignOut = {
                scope.launch {
                    withContext(NonCancellable) { onSignOut() }
                }
            }
        )
        return
    }

    var currentDest by remember { mutableStateOf(MainDestination.DASHBOARD) }
    var moduleTab by remember { mutableStateOf(SovereignModuleTab.REPORTS) }

    if (currentDest != MainDestination.DASHBOARD) {
        BackHandler { currentDest = MainDestination.DASHBOARD }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusBanner()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        viewModel.recordUserInteraction()
                    }
                }
            }
    ) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        MainDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentDest == dest,
                                onClick = { currentDest = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.name}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        MainDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentDest == dest,
                                onClick = { currentDest = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${dest.name}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (cloudState is UiState.Error) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "ملاحظة المزامنة: ${(cloudState as UiState.Error).message}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    when (currentDest) {
                        MainDestination.DASHBOARD -> DashboardScreen(
                            session = session,
                            kpis = kpis,
                            criticalAlert = criticalAlert,
                            travelers = travelers,
                            reports = reports,
                            isSyncing = isSyncing,
                            onDismissAlert = { viewModel.clearCriticalAlert() },
                            onNavigateToScanner = { currentDest = MainDestination.SCANNER },
                            onNavigateToImport = { currentDest = MainDestination.IMPORT },
                            onNavigateToTravelers = { currentDest = MainDestination.TRAVELERS },
                            onNavigateToReports = {
                                moduleTab = SovereignModuleTab.REPORTS
                                currentDest = MainDestination.MODULES
                            },
                            onSyncNow = { viewModel.syncCloudNow() },
                            onExportShiftReport = { viewModel.exportShiftSummaryPdf(context) }
                        )

                        MainDestination.SCANNER -> ScannerScreen(
                            session = session,
                            scannedMrz = scannedMrz,
                            aiState = aiState,
                            criticalAlert = criticalAlert,
                            onMrzDetected = { viewModel.onMrzDetected(context, it) },
                            onQrDetected = { viewModel.onQrPayloadDetected(context, it) },
                            onScanBitmap = { viewModel.scanGalleryBitmap(context, it) },
                            onAnalyzeWithGemini = { viewModel.analyzeBitmapWithGemini(context, it) },
                            onCheckWatchlist = { n, p, l -> viewModel.checkWatchlistAndAlert(context, n, p, l) },
                            onSaveTraveler = { fn, pn, idn, co, ge, bd, ex, port, ag, vt, dst, ta, rr, nt ->
                                viewModel.registerTraveler(context, fn, pn, idn, co, ge, bd, ex, port, ag, vt, dst, ta, rr, nt)
                            },
                            onClearAiState = { viewModel.clearAiState() }
                        )

                        MainDestination.TRAVELERS -> TravelersScreen(
                            session = session,
                            travelers = travelers,
                            travels = travels,
                            onAddTraveler = { fn, pn, idn, co, ge, bd, ex, port, ag, vt, dst, ta, rr, nt ->
                                viewModel.registerTraveler(context, fn, pn, idn, co, ge, bd, ex, port, ag, vt, dst, ta, rr, nt)
                            },
                            onDeleteTraveler = { viewModel.deleteTraveler(it) },
                            onExport = { viewModel.exportAndShareTravelers(context, it) },
                            onRecordCrossing = { trv, dir, dst -> viewModel.recordTravelCrossing(trv, dir, dst) },
                            onNavigateToScanner = { currentDest = MainDestination.SCANNER }
                        )

                        MainDestination.IMPORT -> CentralImportScreen(
                            stagedFiles = stagedFiles,
                            importProgress = importProgress,
                            stagedBatch = stagedBatch,
                            lastCommitResult = lastCommitResult,
                            onStageUris = { viewModel.stageUris(context, it) },
                            onRemoveStagedFile = { viewModel.removeStagedFile(it) },
                            onClearAll = { viewModel.clearStagedFiles() },
                            onAnalyzeFiles = { viewModel.analyzeStagedFiles(context) },
                            onChangeTargetCategory = { viewModel.updateBatchTargetCategory(it) },
                            onUpdateRow = { viewModel.updateStagedRow(it) },
                            onCommitBatch = { viewModel.commitStagedBatch() }
                        )

                        MainDestination.MODULES -> ModulesHubScreen(
                            session = session,
                            initialTab = moduleTab,
                            reports = reports,
                            seizures = seizures,
                            movements = movements,
                            permits = permits,
                            mofaVisas = mofaVisas,
                            foreigners = foreigners,
                            refugees = refugees,
                            migrants = migrants,
                            citizenshipCases = citizenshipCases,
                            agencies = agencies,
                            correspondence = correspondence,
                            onAddReport = { t, tg, p, tp, sv, ds -> viewModel.addReport(t, tg, p, tp, sv, ds) },
                            onUpdateReportStatus = { rep, st -> viewModel.updateReportStatus(rep, st) },
                            onParseIntelligenceText = { viewModel.parseIntelligenceNoteToReport(it) },
                            onAddSeizure = { n, p, r -> viewModel.addSeizure(n, p, r) },
                            onAdvanceSeizure = { sz, rec -> viewModel.advanceSeizureStage(sz, rec) },
                            onAddMovement = { vt, pl, dr, ms, hr -> viewModel.addMovement(vt, pl, dr, ms, hr) },
                            onMarkReturned = { mv, nt -> viewModel.markMovementReturned(mv, nt) },
                            onAddPermit = { d, r, ac, c, vt -> viewModel.addPermit(d, r, ac, c, vt) },
                            onAddMofaVisa = { mn, org, tp, rt, ct -> viewModel.addMofaVisa(mn, org, tp, rt, ct) },
                            onAddForeigner = { rn, nm, ps, nt, em, ex -> viewModel.addForeigner(rn, nm, ps, nt, em, ex) },
                            onAddRefugee = { un, nm, nt, cp -> viewModel.addRefugee(un, nm, nt, cp) },
                            onAddMigrant = { nm, nt, sh -> viewModel.addMigrant(nm, nt, sh) },
                            onAddCitizenshipCase = { st, ap, sn, td -> viewModel.addCitizenshipCase(st, ap, sn, td) },
                            onAddAgency = { nm, tp, gv -> viewModel.addAgency(nm, tp, gv) },
                            onAddCorrespondence = { dir, sb, sm, en -> viewModel.addCorrespondence(dir, sb, sm, en) }
                        )

                        MainDestination.SETTINGS -> SettingsAndAuditScreen(
                            session = session,
                            officers = officers,
                            auditLogs = auditLogs,
                            onUpdateSession = { name, role, port, bio, dark, timeoutMin ->
                                viewModel.updateSession(
                                    officerName = name,
                                    role = role,
                                    assignedPort = port,
                                    biometricEnabled = bio,
                                    highContrastMode = dark,
                                    idleTimeoutMinutes = timeoutMin
                                )
                            },
                            onLockNow = { viewModel.lockSession() },
                            onAddOfficer = { rk, nm, pt, rl, dt -> viewModel.addOfficer(rk, nm, pt, rl, dt) },
                            onSyncNow = { viewModel.syncCloudNow() },
                            onSignOut = {
                                scope.launch {
                                    withContext(NonCancellable) { onSignOut() }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(
    officerName: String,
    portName: String,
    biometricAuth: BiometricAuthentication,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var authError by remember { mutableStateOf<String?>(null) }

    fun triggerUnlockPrompt() {
        if (activity == null) {
            authError = "تعذر ربط نافذة التحقق بالبصمة."
            return
        }
        if (!biometricAuth.canAuthenticate(context)) {
            onUnlocked()
            return
        }
        authError = null
        biometricAuth.authenticate(
            activity = activity,
            title = "تحقق أمني لفتح جلسة الضابط",
            subtitle = "$officerName • $portName",
            onSuccess = onUnlocked,
            onFailed = { authError = "لم تتطابق البصمة — أعد المحاولة" },
            onError = { err -> authError = err },
            onCancelled = { authError = "تم إلغاء التحقق؛ اضغط للمتابعة بالبصمة" }
        )
    }

    LaunchedEffect(activity) {
        triggerUnlockPrompt()
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("biometric_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "قفل البصمة الميداني",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "الجلسة محمية بالبصمة الميدانية",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$officerName — $portName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (authError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = authError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { triggerUnlockPrompt() },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(52.dp).testTag("biometric_unlock_button")
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("فتح القفل بالبصمة")
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.heightIn(min = 48.dp).testTag("biometric_lock_sign_out_button")
            ) {
                Text("تسجيل الخروج من الحساب")
            }
        }
    }
}
