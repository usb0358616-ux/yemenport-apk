package com.example.yemenport.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.yemenport.domain.ai.AiDocumentExtraction
import com.example.yemenport.domain.mrz.MrzParseResult
import com.example.yemenport.domain.mrz.PassportOcrAnalyzer
import com.example.yemenport.domain.security.WatchlistCheckSummary
import com.example.yemenport.ui.OfficerSession
import com.example.yemenport.ui.UiState
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    session: OfficerSession,
    scannedMrz: MrzParseResult?,
    aiState: UiState<AiDocumentExtraction>?,
    criticalAlert: WatchlistCheckSummary?,
    onMrzDetected: (MrzParseResult) -> Unit,
    onScanBitmap: (Bitmap) -> Unit,
    onAnalyzeWithGemini: (Bitmap) -> Unit,
    onCheckWatchlist: (String, String, String) -> WatchlistCheckSummary,
    onSaveTraveler: (
        fullName: String,
        passportName: String,
        idNumber: String,
        country: String,
        gender: String,
        birthDate: String,
        expiryDate: String,
        portName: String,
        agency: String,
        visaType: String,
        destination: String,
        travelAllowed: String,
        returnReason: String,
        notes: String
    ) -> Unit,
    onClearAiState: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraActive by remember { mutableStateOf(false) }
    var liveOcrPreview by remember { mutableStateOf("") }
    var lastSelectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Form fields auto-populated from MRZ / OCR / Gemini AI
    var fullNameArabic by remember { mutableStateOf("") }
    var passportNameLatin by remember { mutableStateOf("") }
    var passportNumber by remember { mutableStateOf("") }
    var nationality by remember { mutableStateOf("اليمن") }
    var gender by remember { mutableStateOf("ذكر") }
    var birthDate by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var agency by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var travelAllowed by remember { mutableStateOf("نعم") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(scannedMrz) {
        if (scannedMrz != null) {
            if (scannedMrz.fullLatinName.isNotBlank()) passportNameLatin = scannedMrz.fullLatinName
            if (fullNameArabic.isBlank() && scannedMrz.fullLatinName.isNotBlank()) fullNameArabic = scannedMrz.fullLatinName
            if (scannedMrz.passportNumber.isNotBlank()) passportNumber = scannedMrz.passportNumber
            if (scannedMrz.nationalityArabic.isNotBlank()) nationality = scannedMrz.nationalityArabic
            if (scannedMrz.gender.isNotBlank()) gender = scannedMrz.gender
            if (scannedMrz.birthDate.isNotBlank()) birthDate = scannedMrz.birthDate
            if (scannedMrz.expiryDate.isNotBlank()) expiryDate = scannedMrz.expiryDate
        }
    }

    LaunchedEffect(aiState) {
        if (aiState is UiState.Success) {
            val ai = aiState.data
            if (ai.fullNameArabic.isNotBlank()) fullNameArabic = ai.fullNameArabic
            if (ai.fullNameLatin.isNotBlank()) passportNameLatin = ai.fullNameLatin
            if (ai.passportNumber.isNotBlank()) passportNumber = ai.passportNumber
            if (ai.nationality.isNotBlank()) nationality = ai.nationality
            if (ai.birthDate.isNotBlank()) birthDate = ai.birthDate
            if (ai.expiryDate.isNotBlank()) expiryDate = ai.expiryDate
            if (ai.summaryArabic.isNotBlank()) notes = ai.summaryArabic
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) cameraActive = true
    }

    // Zero-permission Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            if (bmp != null) {
                lastSelectedBitmap = bmp
                onScanBitmap(bmp)
            }
        }
    }

    val analyzer = remember {
        PassportOcrAnalyzer(
            onMrzDetected = { res ->
                onMrzDetected(res)
                cameraActive = false
            },
            onTextObserved = { txt ->
                liveOcrPreview = txt.take(180)
            }
        )
    }

    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("الماسح الذكي للجوازات والوثائق (CameraX + ML Kit + MRZ)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Action Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        analyzer.resetLock()
                        cameraActive = !cameraActive
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_camera_scan_button")
            ) {
                Icon(if (cameraActive) Icons.Default.VideocamOff else Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (cameraActive) "إيقاف الكاميرا" else "مسح مباشر")
            }

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("pick_passport_image_button")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("من المعرض")
            }
        }

        // CameraX Live Viewfinder
        if (cameraActive && hasCameraPermission) {
            ElevatedCard(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val executor = remember { Executors.newSingleThreadExecutor() }
                    DisposableEffect(Unit) {
                        onDispose { executor.shutdown() }
                    }
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { it.setAnalyzer(executor, analyzer) }
                                runCatching {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Text(
                            text = if (liveOcrPreview.isBlank()) "وجّه الكاميرا نحو سطر MRZ أسفل الجواز (P<YEM...)" else liveOcrPreview,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Optional Gemini Multimodal Analysis for Arabic documents / complex images
        if (lastSelectedBitmap != null) {
            FilledTonalButton(
                onClick = { lastSelectedBitmap?.let(onAnalyzeWithGemini) },
                enabled = aiState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gemini_vision_analyze_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SovereignGold)
                Spacer(Modifier.width(8.dp))
                Text(if (aiState is UiState.Loading) "جاري تحليل الوثيقة عبر Gemini 3.8..." else "تحليل الوثيقة العربية/المختلطة عبر Gemini AI")
            }
        }

        if (aiState is UiState.Error) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(aiState.message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearAiState) { Text("إغلاق") }
                }
            }
        }

        // MRZ Verification Status Card
        if (scannedMrz != null) {
            ElevatedCard(
                colors = CardDefaults.cardColors(
                    containerColor = if (scannedMrz.allChecksPassed) SovereignEmerald.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "نتيجة القراءة الآلية (${scannedMrz.format}) • العمر المحسوب: ${scannedMrz.age} سنة • حالة الجواز: ${scannedMrz.passportStatus}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Check Digits -> الجواز: ${if (scannedMrz.passportCheckValid) "صحيح ✓" else "تحقق يدوياً"} | الميلاد: ${if (scannedMrz.birthCheckValid) "صحيح ✓" else "-"} | الانتهاء: ${if (scannedMrz.expiryCheckValid) "صحيح ✓" else "-"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Watchlist Alert Feedback inside Scanner
        if (criticalAlert != null && criticalAlert.isCritical) {
            Card(colors = CardDefaults.cardColors(containerColor = SovereignCrimson), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🚨 تطابق فوري مع قائمة المطلوبين/المضبوطات!", color = Color.White, fontWeight = FontWeight.Bold)
                    criticalAlert.hits.forEach {
                        Text("• ${it.source.arabicLabel}: ${it.matchedName} — ${it.reason}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Traveler Registration Form (Auto-filled & Editable)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("بيانات المسافر المستخرجة (قابلة للتعديل والحفظ)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = fullNameArabic,
                    onValueChange = { fullNameArabic = it },
                    label = { Text("الاسم الكامل (عربي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("scanner_name_input")
                )
                OutlinedTextField(
                    value = passportNameLatin,
                    onValueChange = { passportNameLatin = it },
                    label = { Text("الاسم كما بالجواز (لاتيني)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passportNumber,
                        onValueChange = { passportNumber = it },
                        label = { Text("رقم الجواز") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("scanner_passport_input")
                    )
                    OutlinedTextField(
                        value = nationality,
                        onValueChange = { nationality = it },
                        label = { Text("الجنسية") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("تاريخ الميلاد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        label = { Text("تاريخ الانتهاء") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = agency,
                        onValueChange = { agency = it },
                        label = { Text("الوكالة / الجهة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("الوجهة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == "ذكر",
                        onClick = { gender = "ذكر" },
                        label = { Text("ذكر") }
                    )
                    FilterChip(
                        selected = gender == "أنثى",
                        onClick = { gender = "أنثى" },
                        label = { Text("أنثى") }
                    )
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = travelAllowed == "نعم",
                        onClick = { travelAllowed = "نعم" },
                        label = { Text("مسموح بالسفر") }
                    )
                    FilterChip(
                        selected = travelAllowed == "لا",
                        onClick = { travelAllowed = "لا" },
                        label = { Text("منع سفر") }
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات المعاينة") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onCheckWatchlist(fullNameArabic, passportNumber, passportNameLatin) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("scanner_check_watchlist_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("مباينة فورية")
                    }
                    Button(
                        onClick = {
                            onSaveTraveler(
                                fullNameArabic,
                                passportNameLatin,
                                passportNumber,
                                nationality,
                                gender,
                                birthDate,
                                expiryDate,
                                session.assignedPort,
                                agency,
                                "",
                                destination,
                                travelAllowed,
                                "",
                                notes
                            )
                            fullNameArabic = ""
                            passportNameLatin = ""
                            passportNumber = ""
                            birthDate = ""
                            expiryDate = ""
                            notes = ""
                        },
                        enabled = fullNameArabic.isNotBlank() || passportNumber.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("scanner_save_traveler_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("اعتماد وحفظ")
                    }
                }
            }
        }
    }
}
