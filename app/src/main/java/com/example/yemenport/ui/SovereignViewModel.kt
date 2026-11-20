package com.example.yemenport.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yemenport.core.OfficerRole
import com.example.yemenport.data.local.*
import com.example.yemenport.data.repository.SovereignRepository
import com.example.yemenport.domain.ai.AiDocumentExtraction
import com.example.yemenport.domain.ai.SovereignAiRepository
import com.example.yemenport.domain.export.SovereignExportEngine
import com.example.yemenport.domain.importengine.*
import com.example.yemenport.domain.mrz.MrzParseResult
import com.example.yemenport.domain.mrz.PassportOcrAnalyzer
import com.example.yemenport.domain.security.WatchlistCheckSummary
import com.example.yemenport.domain.security.WatchlistGuardEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class OfficerSession(
    val officerName: String = "ضابط المنفذ المناوب",
    val role: OfficerRole = OfficerRole.DIRECTOR_GENERAL,
    val assignedPort: String = "المركز الرئيسي - العاصمة صنعاء",
    val biometricEnabled: Boolean = false,
    val highContrastMode: Boolean = false,
    val hqCenterName: String = "مصلحة الهجرة والجوازات — صنعاء"
)

data class DashboardKpis(
    val totalTravelers: Int = 0,
    val allowedCount: Int = 0,
    val blockedOrReturnedCount: Int = 0,
    val activeReportsCount: Int = 0,
    val seizedPassportsCount: Int = 0,
    val activePatrolsCount: Int = 0,
    val mofaRequestsCount: Int = 0,
    val activePermitsCount: Int = 0,
    val unsyncedCount: Int = 0
)

class SovereignViewModel(
    private val repository: SovereignRepository,
    private val aiRepository: SovereignAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _session = MutableStateFlow(OfficerSession())
    val session: StateFlow<OfficerSession> = _session.asStateFlow()

    val travelers: StateFlow<List<TravelerEntity>> = repository.travelers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val reports: StateFlow<List<SecurityReportEntity>> = repository.reports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val movements: StateFlow<List<PatrolMovementEntity>> = repository.movements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val seizures: StateFlow<List<PassportSeizureEntity>> = repository.seizures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val migrants: StateFlow<List<UndocumentedMigrantEntity>> = repository.migrants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val refugees: StateFlow<List<RefugeeRecordEntity>> = repository.refugees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val foreigners: StateFlow<List<ForeignerResidentEntity>> = repository.foreigners
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val permits: StateFlow<List<MovementPermitEntity>> = repository.permits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val citizenshipCases: StateFlow<List<CitizenshipCaseEntity>> = repository.citizenshipCases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val agencies: StateFlow<List<TravelAgencyEntity>> = repository.agencies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val correspondence: StateFlow<List<CorrespondenceEntity>> = repository.correspondence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val mofaVisas: StateFlow<List<MofaVisaEntity>> = repository.mofaVisas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val officers: StateFlow<List<PortOfficerEntity>> = repository.officers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val learnedPatterns: StateFlow<List<LearnedPatternEntity>> = repository.learnedPatterns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // Two-tier Flow error handling for Firestore profile listener
    val cloudProfileState: StateFlow<UiState<OfficerSession>> = repository.observeProfileSettings(currentUserId)
        .map<_, UiState<OfficerSession>> { fs ->
            if (fs != null) {
                val updated = OfficerSession(
                    officerName = fs.officerName.ifBlank { _session.value.officerName },
                    role = OfficerRole.fromTitle(fs.officerRole),
                    assignedPort = fs.assignedPort.ifBlank { _session.value.assignedPort },
                    biometricEnabled = fs.biometricEnabled,
                    highContrastMode = fs.highContrastMode,
                    hqCenterName = fs.hqCenterName.ifBlank { _session.value.hqCenterName }
                )
                _session.value = updated
                UiState.Success(updated)
            } else {
                UiState.Success(_session.value)
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing cloud profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "تعذر الاتصال بإعدادات السحابة؛ العمل مستمر أوفلاين"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val kpis: StateFlow<DashboardKpis> = combine(
        travelers,
        reports,
        seizures,
        movements,
        combine(mofaVisas, permits) { m, p -> m.size to p.size }
    ) { trv, rep, sz, mov, (mofaCount, permitCount) ->
        DashboardKpis(
            totalTravelers = trv.size,
            allowedCount = trv.count { it.travelAllowed == "نعم" },
            blockedOrReturnedCount = trv.count { it.travelAllowed == "لا" || it.status.contains("مرتجع") || it.returnReason.isNotBlank() },
            activeReportsCount = rep.count { it.status != "مرفوض" },
            seizedPassportsCount = sz.count { it.status != "تم التسليم" },
            activePatrolsCount = mov.count { it.status == "في مهمة" },
            mofaRequestsCount = mofaCount,
            activePermitsCount = permitCount,
            unsyncedCount = trv.count { !it.synced } + rep.count { !it.synced } + sz.count { !it.synced }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardKpis())

    private val _criticalAlert = MutableStateFlow<WatchlistCheckSummary?>(null)
    val criticalAlert: StateFlow<WatchlistCheckSummary?> = _criticalAlert.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Scanner & AI State
    private val _scannedMrz = MutableStateFlow<MrzParseResult?>(null)
    val scannedMrz: StateFlow<MrzParseResult?> = _scannedMrz.asStateFlow()

    private val _aiState = MutableStateFlow<UiState<AiDocumentExtraction>?>(null)
    val aiState: StateFlow<UiState<AiDocumentExtraction>?> = _aiState.asStateFlow()

    // Central Import State
    private val _stagedFiles = MutableStateFlow<List<StagedFileItem>>(emptyList())
    val stagedFiles: StateFlow<List<StagedFileItem>> = _stagedFiles.asStateFlow()

    private val _importProgress = MutableStateFlow<Pair<Int, String>?>(null)
    val importProgress: StateFlow<Pair<Int, String>?> = _importProgress.asStateFlow()

    private val _stagedBatch = MutableStateFlow<StagedImportBatch?>(null)
    val stagedBatch: StateFlow<StagedImportBatch?> = _stagedBatch.asStateFlow()

    private val _lastCommitResult = MutableStateFlow<CommitImportResult?>(null)
    val lastCommitResult: StateFlow<CommitImportResult?> = _lastCommitResult.asStateFlow()

    fun clearStatusBanner() { _statusBanner.value = null }
    fun clearCriticalAlert() { _criticalAlert.value = null }
    fun clearAiState() { _aiState.value = null }

    fun updateSession(
        officerName: String = _session.value.officerName,
        role: OfficerRole = _session.value.role,
        assignedPort: String = _session.value.assignedPort,
        biometricEnabled: Boolean = _session.value.biometricEnabled,
        highContrastMode: Boolean = _session.value.highContrastMode,
        hqCenterName: String = _session.value.hqCenterName
    ) {
        val next = OfficerSession(officerName, role, assignedPort, biometricEnabled, highContrastMode, hqCenterName)
        _session.value = next
        viewModelScope.launch {
            repository.saveProfileSettings(
                userId = currentUserId,
                officerName = next.officerName,
                officerRole = next.role.arabicTitle,
                assignedPort = next.assignedPort,
                biometricEnabled = next.biometricEnabled,
                highContrastMode = next.highContrastMode,
                hqCenterName = next.hqCenterName
            )
        }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.syncWithCloud(currentUserId)
            _isSyncing.value = false
            res.onSuccess { (pushed, pulled) ->
                _statusBanner.value = "اكتملت المزامنة السحابية: رفع $pushed سجل، تحديث $pulled سجل"
            }.onFailure { err ->
                _statusBanner.value = "تعذر المزامنة حالياً (البيانات محفوظة محلياً): ${err.localizedMessage ?: "غير متصل"}"
            }
        }
    }

    fun checkWatchlistAndAlert(context: Context, name: String, passport: String, latinName: String = ""): WatchlistCheckSummary {
        val summary = WatchlistGuardEngine.checkWatchlist(
            queryName = name,
            queryPassport = passport,
            queryLatinName = latinName,
            travelers = travelers.value,
            reports = reports.value,
            seizures = seizures.value
        )
        if (summary.isCritical) {
            _criticalAlert.value = summary
            WatchlistGuardEngine.triggerCriticalFeedback(context)
            viewModelScope.launch {
                val s = _session.value
                repository.logAudit(
                    action = "تنبيه قائمة مطلوبين",
                    entityType = "WatchlistGuard",
                    entityId = passport,
                    details = "تطابق حرج للاسم ($name / $latinName) جواز ($passport): ${summary.hits.firstOrNull()?.reason}",
                    officerName = s.officerName,
                    officerRole = s.role.arabicTitle,
                    portName = s.assignedPort
                )
            }
        }
        return summary
    }

    fun onMrzDetected(context: Context, result: MrzParseResult) {
        _scannedMrz.value = result
        checkWatchlistAndAlert(
            context = context,
            name = result.fullLatinName,
            passport = result.passportNumber,
            latinName = result.fullLatinName
        )
    }

    fun scanGalleryBitmap(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.Default) {
                PassportOcrAnalyzer.recognizeBitmap(bitmap)
            }
            val parsed = PassportOcrAnalyzer.extractPassportFieldsFromText(raw)
            onMrzDetected(context, parsed)
        }
    }

    fun analyzeBitmapWithGemini(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            _aiState.value = UiState.Loading
            val result = aiRepository.analyzeDocumentImage(bitmap)
            result.onSuccess { extracted ->
                _aiState.value = UiState.Success(extracted)
                if (extracted.passportNumber.isNotBlank() || extracted.fullNameArabic.isNotBlank()) {
                    checkWatchlistAndAlert(
                        context = context,
                        name = extracted.fullNameArabic,
                        passport = extracted.passportNumber,
                        latinName = extracted.fullNameLatin
                    )
                }
            }.onFailure { err ->
                _aiState.value = UiState.Error(err.localizedMessage ?: "تعذر التحليل عبر Gemini")
            }
        }
    }

    fun registerTraveler(
        context: Context,
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
        travelAllowedInput: String,
        returnReason: String,
        notes: String
    ) {
        viewModelScope.launch {
            val alert = checkWatchlistAndAlert(context, fullName, idNumber, passportName)
            val effectiveAllowed = if (alert.isCritical || travelAllowedInput == "لا") "لا" else "نعم"
            val effectiveVetting = if (alert.isCritical) "🚨 ${alert.hits.first().source.arabicLabel}" else if (effectiveAllowed == "لا") "ممنوع من السفر" else "سليم"
            val age = PassportOcrAnalyzer.calculateAge(birthDate)
            val passStatus = PassportOcrAnalyzer.evaluatePassportStatus(expiryDate)

            val s = _session.value
            val entity = TravelerEntity(
                id = "trv_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                fullName = fullName.ifBlank { passportName }.ifBlank { "مسافر" },
                passportName = passportName,
                idNumber = PassportOcrAnalyzer.normalizeDigits(idNumber).trim(),
                country = country.ifBlank { "اليمن" },
                gender = gender,
                birthDate = birthDate,
                age = age,
                expiryDate = expiryDate,
                passportStatus = passStatus,
                portName = portName.ifBlank { s.assignedPort },
                center = portName.ifBlank { s.assignedPort },
                agency = agency,
                visaType = visaType,
                destination = destination,
                travelAllowed = effectiveAllowed,
                vettingResult = effectiveVetting,
                returnReason = returnReason,
                status = if (effectiveAllowed == "لا") "مرتجع" else "معتمد",
                investigatingOfficer = s.officerName,
                additionalInfo = notes
            )
            val (saved, merged) = repository.saveTravelerWithSmartMerge(
                incoming = entity,
                officerName = s.officerName,
                officerRole = s.role.arabicTitle,
                portName = s.assignedPort
            )
            _statusBanner.value = if (merged) {
                "تم الدمج الذكي لتحديثات الجواز ${saved.idNumber}"
            } else {
                "تم حفظ سجل المسافر ${saved.fullName} بنجاح"
            }
        }
    }

    fun deleteTraveler(id: String) {
        val s = _session.value
        if (!s.role.canDeleteRecords) {
            _statusBanner.value = "صلاحيتك (${s.role.arabicTitle}) لا تسمح بحذف السجلات"
            return
        }
        viewModelScope.launch {
            repository.deleteTraveler(currentUserId, id, s.officerName, s.role.arabicTitle, s.assignedPort)
            _statusBanner.value = "تم حذف السجل وتوثيق العملية في سجل التدقيق"
        }
    }

    // Batch Import Staging
    fun stageUris(context: Context, uris: List<Uri>) {
        val existingUris = _stagedFiles.value.map { it.uri }.toSet()
        val added = uris.filterNot { it in existingUris }.map { UnifiedDocumentParser.inspectUri(context, it) }
        _stagedFiles.value = _stagedFiles.value + added
    }

    fun removeStagedFile(id: String) {
        _stagedFiles.value = _stagedFiles.value.filterNot { it.id == id }
    }

    fun clearStagedFiles() {
        _stagedFiles.value = emptyList()
        _stagedBatch.value = null
        _lastCommitResult.value = null
    }

    fun analyzeStagedFiles(context: Context) {
        val files = _stagedFiles.value
        if (files.isEmpty()) return
        viewModelScope.launch {
            _lastCommitResult.value = null
            _importProgress.value = 5 to "بدء التجهيز والفحص الذكي..."
            val batch = UnifiedDocumentParser.parseStagedFiles(
                context = context,
                files = files,
                defaultPort = _session.value.assignedPort,
                travelers = travelers.value,
                reports = reports.value,
                seizures = seizures.value,
                learned = learnedPatterns.value
            ) { pct, msg ->
                _importProgress.value = pct to msg
            }
            _stagedBatch.value = batch
            _importProgress.value = null
            val hits = batch.rows.count { it.watchlistWarning.isNotBlank() }
            if (hits > 0) {
                WatchlistGuardEngine.triggerCriticalFeedback(context)
                _statusBanner.value = "تحذير أمني: تم رصد $hits تطابق مع قوائم المطلوبين ضمن الملفات المستوردة!"
            }
        }
    }

    fun updateBatchTargetCategory(category: ImportTargetCategory) {
        _stagedBatch.value = _stagedBatch.value?.copy(targetCategory = category)
    }

    fun updateStagedRow(updated: EditableImportRow) {
        val cur = _stagedBatch.value ?: return
        _stagedBatch.value = cur.copy(
            rows = cur.rows.map { if (it.rowId == updated.rowId) updated else it }
        )
    }

    fun commitStagedBatch() {
        val batch = _stagedBatch.value ?: return
        val s = _session.value
        viewModelScope.launch {
            _importProgress.value = 50 to "ترحيل السجلات إلى قاعدة البيانات..."
            val res = UnifiedDocumentParser.commitBatch(
                dao = repository.dao,
                batch = batch,
                officerName = s.officerName,
                officerRole = s.role.arabicTitle,
                defaultPort = s.assignedPort
            )
            _importProgress.value = null
            _lastCommitResult.value = res
            _stagedBatch.value = null
            _stagedFiles.value = emptyList()
            _statusBanner.value = "تم ترحيل ${res.committedCount} سجل إلى ${res.targetCategory.arabicTitle}"
        }
    }

    // Specialized Entities Actions
    fun addReport(title: String, targetName: String, passportNo: String, type: String, severity: String, desc: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveReport(
                SecurityReportEntity(
                    id = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    title = title,
                    targetName = targetName,
                    targetPassportNumber = PassportOcrAnalyzer.normalizeDigits(passportNo),
                    reportType = type,
                    severity = severity,
                    description = desc,
                    assignedPort = s.assignedPort,
                    officerName = s.officerName
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun updateReportStatus(report: SecurityReportEntity, newStatus: String) {
        val s = _session.value
        if (newStatus == "معتمد" && !s.role.canApproveBan) {
            _statusBanner.value = "اعتماد الحظر والتعميم يتطلب صلاحية محقق أول أو قيادة"
            return
        }
        viewModelScope.launch {
            repository.saveReport(report.copy(status = newStatus), s.officerName, s.role.arabicTitle, s.assignedPort)
        }
    }

    fun parseIntelligenceNoteToReport(rawText: String) {
        val clean = UnifiedDocumentParser.cleanArabicText(rawText)
        val pass = Regex("\\b([A-Z0-9]{6,11})\\b").find(clean.uppercase())?.value.orEmpty()
        addReport(
            title = "بلاغ مستخلص آلياً",
            targetName = clean.lines().firstOrNull()?.take(80) ?: "مشتبه به",
            passportNo = pass,
            type = "تحليل نص استخباراتي",
            severity = "عالي",
            desc = clean.take(600)
        )
        _statusBanner.value = "تم تحليل النص وتقييده كبلاغ أمني"
    }

    fun addMovement(vehicleType: String, plate: String, driver: String, mission: String, hours: Int) {
        val s = _session.value
        val activeDuplicate = movements.value.any { it.status == "في مهمة" && it.plateNumber.equals(plate.trim(), ignoreCase = true) }
        if (activeDuplicate) {
            _statusBanner.value = "تنبيه: الدورية ذات اللوحة ($plate) لا تزال مسجلة في مهمة نشطة!"
            return
        }
        val now = java.time.LocalDateTime.now()
        val exp = now.plusHours(hours.toLong())
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        viewModelScope.launch {
            repository.saveMovement(
                PatrolMovementEntity(
                    id = "mov_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    vehicleType = vehicleType,
                    plateNumber = plate.trim(),
                    driverName = driver.trim(),
                    portName = s.assignedPort,
                    mission = mission,
                    departureTime = now.format(fmt),
                    expectedReturnTime = exp.format(fmt),
                    status = "في مهمة"
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun markMovementReturned(movement: PatrolMovementEntity, notes: String) {
        val s = _session.value
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        viewModelScope.launch {
            repository.saveMovement(
                movement.copy(
                    status = "عاد",
                    actualReturnTime = java.time.LocalDateTime.now().format(fmt),
                    outcomeNotes = notes.ifBlank { "عادت الدورية بسلام" }
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addSeizure(travelerName: String, passportNo: String, reason: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveSeizure(
                PassportSeizureEntity(
                    id = "sz_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    recordNumber = "SZ-${System.currentTimeMillis().toString().takeLast(5)}",
                    travelerName = travelerName,
                    passportNumber = PassportOcrAnalyzer.normalizeDigits(passportNo),
                    seizurePort = s.assignedPort,
                    seizureDate = LocalDate.now().toString(),
                    reason = reason,
                    status = "جديد"
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun advanceSeizureStage(seizure: PassportSeizureEntity, recipientName: String = "") {
        val s = _session.value
        val next = when (seizure.status) {
            "جديد" -> "بانتظار الفحص"
            "بانتظار الفحص" -> "تم الفحص"
            "تم الفحص" -> "محال"
            else -> "تم التسليم"
        }
        viewModelScope.launch {
            repository.saveSeizure(
                seizure.copy(
                    status = next,
                    recipientName = if (next == "تم التسليم") recipientName.ifBlank { seizure.travelerName } else seizure.recipientName,
                    deliveredAt = if (next == "تم التسليم") LocalDate.now().toString() else seizure.deliveredAt
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addPermit(delegation: String, route: String, accommodation: String, passengers: Int, validTo: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.savePermit(
                MovementPermitEntity(
                    id = "prm_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    permitCode = "YMP-${System.currentTimeMillis().toString().takeLast(6)}",
                    delegationName = delegation,
                    authorizedRoute = route,
                    accommodationPlace = accommodation,
                    passengersCount = passengers.coerceAtLeast(1),
                    validFrom = LocalDate.now().toString(),
                    validTo = validTo.ifBlank { LocalDate.now().plusDays(7).toString() }
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addMofaVisa(memoNumber: String, org: String, type: String, route: String, count: Int) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveMofaVisa(
                MofaVisaEntity(
                    id = "mofa_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    memoNumber = memoNumber,
                    memoDate = LocalDate.now().toString(),
                    organization = org,
                    requestType = type,
                    flightRoute = route,
                    beneficiariesCount = count.coerceAtLeast(1)
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addForeigner(resNo: String, name: String, passport: String, nat: String, employer: String, expiry: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveForeigner(
                ForeignerResidentEntity(
                    id = "frg_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    residenceNumber = resNo,
                    fullName = name,
                    passportNumber = passport,
                    nationality = nat,
                    employer = employer,
                    residenceExpiryDate = expiry
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addRefugee(unhcrNo: String, name: String, nat: String, camp: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveRefugee(
                RefugeeRecordEntity(
                    id = "ref_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    unhcrNumber = unhcrNo,
                    fullName = name,
                    nationality = nat,
                    campOrCity = camp
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addMigrant(name: String, nat: String, shelter: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveMigrant(
                UndocumentedMigrantEntity(
                    id = "mig_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    fullName = name,
                    nationality = nat,
                    apprehensionPort = s.assignedPort,
                    apprehensionDate = LocalDate.now().toString(),
                    shelterLocation = shelter
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addCitizenshipCase(serviceType: String, applicant: String, spouseNat: String, targetDays: Int) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveCitizenshipCase(
                CitizenshipCaseEntity(
                    id = "cit_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    caseNumber = "CIT-${System.currentTimeMillis().toString().takeLast(5)}",
                    serviceType = serviceType,
                    applicantName = applicant,
                    spouseNationality = spouseNat,
                    submissionDate = LocalDate.now().toString(),
                    slaDaysTarget = targetDays
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addAgency(name: String, type: String, governorate: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveAgency(
                TravelAgencyEntity(
                    id = "cmp_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    agencyName = name,
                    companyType = type,
                    governorate = governorate,
                    center = s.assignedPort
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addCorrespondence(direction: String, subject: String, summary: String, entityName: String) {
        val s = _session.value
        viewModelScope.launch {
            repository.saveCorrespondence(
                CorrespondenceEntity(
                    id = "cor_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    referenceNumber = "MSG-${System.currentTimeMillis().toString().takeLast(5)}",
                    direction = direction,
                    subject = subject,
                    summary = summary,
                    senderEntity = if (direction == "وارد") entityName else s.assignedPort,
                    recipientEntity = if (direction == "صادر") entityName else s.assignedPort,
                    gregorianDate = LocalDate.now().toString()
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun addOfficer(rank: String, name: String, port: String, role: String, duty: Boolean) {
        val s = _session.value
        if (!s.role.canManageUsers) {
            _statusBanner.value = "إدارة الضباط محصورة على القيادة ومدراء الإدارات"
            return
        }
        viewModelScope.launch {
            repository.saveOfficer(
                PortOfficerEntity(
                    id = "off_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    rank = rank,
                    fullName = name,
                    assignedPort = port,
                    role = role,
                    dutyOfficer = duty
                ),
                s.officerName, s.role.arabicTitle, s.assignedPort
            )
        }
    }

    fun exportAndShareTravelers(context: Context, format: String) {
        viewModelScope.launch {
            val list = travelers.value
            val fileAndMime = withContext(Dispatchers.IO) {
                when (format) {
                    "XLSX" -> SovereignExportEngine.exportTravelersXlsx(context, list) to
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "PDF" -> SovereignExportEngine.exportSummaryPdf(
                        context = context,
                        title = "تقرير حركة المسافرين (${list.size} سجل)",
                        lines = list.take(60).mapIndexed { i, t ->
                            "${i + 1}. ${t.fullName} | جواز: ${t.idNumber} | ${t.country} | السماح: ${t.travelAllowed}"
                        }
                    ) to "application/pdf"
                    else -> SovereignExportEngine.exportTravelersCsv(context, list) to "text/csv"
                }
            }
            SovereignExportEngine.shareFile(context, fileAndMime.first, fileAndMime.second)
        }
    }

    private companion object {
        const val TAG = "SovereignVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
