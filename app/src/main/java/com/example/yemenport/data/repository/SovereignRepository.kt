package com.example.yemenport.data.repository

import com.example.yemenport.data.local.*
import com.example.yemenport.data.remote.FirestoreSyncRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class SovereignRepository(
    val dao: BorderDao,
    private val firestoreSync: FirestoreSyncRepository
) {
    // Reactive Local Streams (Offline-First Source of Truth)
    val travelers: Flow<List<TravelerEntity>> = dao.observeTravelers()
    val travels: Flow<List<TravelEntity>> = dao.observeTravels()
    val reports: Flow<List<SecurityReportEntity>> = dao.observeReports()
    val movements: Flow<List<PatrolMovementEntity>> = dao.observeMovements()
    val seizures: Flow<List<PassportSeizureEntity>> = dao.observeSeizures()
    val migrants: Flow<List<UndocumentedMigrantEntity>> = dao.observeMigrants()
    val refugees: Flow<List<RefugeeRecordEntity>> = dao.observeRefugees()
    val foreigners: Flow<List<ForeignerResidentEntity>> = dao.observeForeigners()
    val permits: Flow<List<MovementPermitEntity>> = dao.observePermits()
    val citizenshipCases: Flow<List<CitizenshipCaseEntity>> = dao.observeCitizenshipCases()
    val agencies: Flow<List<TravelAgencyEntity>> = dao.observeAgencies()
    val correspondence: Flow<List<CorrespondenceEntity>> = dao.observeCorrespondence()
    val mofaVisas: Flow<List<MofaVisaEntity>> = dao.observeMofaVisas()
    val officers: Flow<List<PortOfficerEntity>> = dao.observeOfficers()
    val auditLogs: Flow<List<AuditLogEntity>> = dao.observeAuditLogs()
    val learnedPatterns: Flow<List<LearnedPatternEntity>> = dao.observeLearnedPatterns()

    fun observeProfileSettings(userId: String) = firestoreSync.observeProfileSettings(userId)

    suspend fun saveProfileSettings(
        userId: String,
        officerName: String,
        officerRole: String,
        assignedPort: String,
        biometricEnabled: Boolean,
        highContrastMode: Boolean,
        hqCenterName: String
    ): Result<Unit> = firestoreSync.saveProfileSettings(
        userId = userId,
        officerName = officerName,
        officerRole = officerRole,
        assignedPort = assignedPort,
        biometricEnabled = biometricEnabled,
        highContrastMode = highContrastMode,
        hqCenterName = hqCenterName
    )

    suspend fun logAudit(
        action: String,
        entityType: String,
        entityId: String,
        details: String,
        officerName: String,
        officerRole: String,
        portName: String,
        severityLevel: AuditSeverity = AuditSeverity.INFO,
        metadata: Map<String, String> = emptyMap()
    ) {
        val now = Instant.now()
        dao.insertAuditLog(
            AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details,
                officerName = officerName,
                officerRole = officerRole,
                portName = portName,
                timestampIso = now.toString(),
                severityLevel = severityLevel,
                metadata = metadata,
                recordedAt = now,
                synced = false
            )
        )
    }

    /**
     * Smart Merge when registering or importing a traveler matching an existing passport number.
     */
    suspend fun saveTravelerWithSmartMerge(
        incoming: TravelerEntity,
        officerName: String,
        officerRole: String,
        portName: String
    ): Pair<TravelerEntity, Boolean> {
        val passport = incoming.idNumber.trim()
        val existing = if (passport.isNotBlank()) dao.findTravelerByPassport(passport) else null
        if (existing == null) {
            val flags = buildList {
                if (incoming.travelAllowed == "لا") add("ممنوع_من_السفر")
                if (incoming.passportStatus != "صالح") add("جواز_${incoming.passportStatus}")
                addAll(incoming.securityFlags)
            }.distinct()
            val saved = incoming.copy(
                id = incoming.id.ifBlank { "trv_${UUID.randomUUID().toString().replace("-", "").take(16)}" },
                securityFlags = flags,
                synced = false,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertTraveler(saved)
            dao.upsertTravel(
                TravelEntity(
                    id = "tr_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    travelerId = saved.id,
                    travelerName = saved.fullName,
                    passportNumber = saved.idNumber,
                    portName = saved.portName.ifBlank { portName },
                    direction = if (saved.exitDate.isNotBlank()) "مغادرة" else "عبور",
                    destination = saved.destination,
                    travelAllowed = saved.travelAllowed,
                    clearanceStatus = TravelClearanceStatus.fromTravelAllowedFlag(saved.travelAllowed),
                    vettingStatus = saved.vettingResult,
                    officerName = officerName,
                    flags = flags,
                    metadata = mapOf("agency" to saved.agency, "visaType" to saved.visaType).filterValues { it.isNotBlank() }
                )
            )
            logAudit(
                action = "إضافة مسافر",
                entityType = "Traveler",
                entityId = saved.id,
                details = "تسجيل ${saved.fullName} (جواز: ${saved.idNumber}) — سماح العبور: ${saved.travelAllowed}",
                officerName = officerName,
                officerRole = officerRole,
                portName = portName,
                severityLevel = if (saved.travelAllowed == "لا") AuditSeverity.CRITICAL else AuditSeverity.INFO,
                metadata = mapOf("passport" to saved.idNumber, "travelAllowed" to saved.travelAllowed)
            )
            return saved to false
        }

        val diffs = mutableListOf<String>()
        fun pick(oldVal: String, newVal: String, label: String): String {
            return if (newVal.isNotBlank() && newVal != oldVal) {
                diffs.add("$label: '$oldVal' -> '$newVal'")
                newVal
            } else oldVal
        }

        val effectiveAllowed = if (existing.travelAllowed == "لا" || incoming.travelAllowed == "لا") "لا" else "نعم"
        val updatedFlags = buildList {
            addAll(existing.securityFlags)
            addAll(incoming.securityFlags)
            if (effectiveAllowed == "لا") add("ممنوع_من_السفر")
        }.distinct()
        val nowIso = Instant.now().toString()
        val mergeEntry = MergeHistoryEntry(
            timestampIso = nowIso.take(16),
            officerName = officerName,
            portName = portName,
            changedFields = diffs.toList(),
            note = if (diffs.isEmpty()) "تأكيد بيانات مطابقة" else "تحديث ذكي عبر المنفذ"
        )

        val merged = existing.copy(
            fullName = pick(existing.fullName, incoming.fullName, "الاسم"),
            passportName = pick(existing.passportName, incoming.passportName, "الاسم اللاتيني"),
            country = pick(existing.country, incoming.country, "الجنسية"),
            birthDate = pick(existing.birthDate, incoming.birthDate, "تاريخ الميلاد"),
            expiryDate = pick(existing.expiryDate, incoming.expiryDate, "انتهاء الجواز"),
            passportStatus = pick(existing.passportStatus, incoming.passportStatus, "حالة الجواز"),
            portName = pick(existing.portName, incoming.portName, "المنفذ"),
            agency = pick(existing.agency, incoming.agency, "الوكالة"),
            visaType = pick(existing.visaType, incoming.visaType, "التأشيرة"),
            destination = pick(existing.destination, incoming.destination, "الوجهة"),
            travelAllowed = effectiveAllowed,
            vettingResult = if (existing.travelAllowed == "لا") existing.vettingResult else incoming.vettingResult,
            returnReason = pick(existing.returnReason, incoming.returnReason, "سبب الإرتجاع"),
            additionalInfo = pick(existing.additionalInfo, incoming.additionalInfo, "ملاحظات"),
            securityFlags = updatedFlags,
            mergeRecords = (existing.mergeRecords + mergeEntry).takeLast(30),
            mergeHistory = buildString {
                if (existing.mergeHistory.isNotBlank()) {
                    append(existing.mergeHistory)
                    append("\n")
                }
                append("[${nowIso.take(16)}] دمج بواسطة $officerName: ")
                append(if (diffs.isEmpty()) "تأكيد بيانات مطابقة" else diffs.joinToString(" | "))
            }.take(1950),
            synced = false,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertTraveler(merged)
        dao.upsertTravel(
            TravelEntity(
                id = "tr_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                travelerId = merged.id,
                travelerName = merged.fullName,
                passportNumber = merged.idNumber,
                portName = merged.portName.ifBlank { portName },
                direction = if (incoming.exitDate.isNotBlank()) "مغادرة" else "تحديث_عبور",
                destination = merged.destination,
                travelAllowed = merged.travelAllowed,
                clearanceStatus = TravelClearanceStatus.fromTravelAllowedFlag(merged.travelAllowed),
                vettingStatus = merged.vettingResult,
                officerName = officerName,
                flags = updatedFlags,
                metadata = mapOf("mergedDiffs" to diffs.size.toString())
            )
        )
        logAudit(
            action = "دمج ذكي لمسافر",
            entityType = "Traveler",
            entityId = merged.id,
            details = "دمج تحديثات الجواز $passport (${diffs.size} تغييرات)",
            officerName = officerName,
            officerRole = officerRole,
            portName = portName,
            severityLevel = if (merged.travelAllowed == "لا") AuditSeverity.WARN else AuditSeverity.INFO,
            metadata = mapOf("passport" to passport, "changesCount" to diffs.size.toString())
        )
        return merged to true
    }

    suspend fun deleteTraveler(userId: String, id: String, officerName: String, officerRole: String, portName: String) {
        dao.deleteTraveler(id)
        firestoreSync.deleteDocument(userId, "travelers", id)
        logAudit("حذف مسافر", "Traveler", id, "حذف سجل المسافر رقم $id", officerName, officerRole, portName)
    }

    suspend fun saveReport(item: SecurityReportEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertReport(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("بلاغ أمني", "SecurityReport", item.id, "${item.title} — ${item.targetName} (${item.status})", officerName, officerRole, portName)
    }

    suspend fun deleteReport(userId: String, id: String, officerName: String, officerRole: String, portName: String) {
        dao.deleteReport(id)
        firestoreSync.deleteDocument(userId, "reports", id)
        logAudit("حذف بلاغ", "SecurityReport", id, "إزالة البلاغ رقم $id", officerName, officerRole, portName)
    }

    suspend fun saveMovement(item: PatrolMovementEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertMovement(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("حركة دورية", "PatrolMovement", item.id, "${item.vehicleType} ${item.plateNumber} — ${item.status}", officerName, officerRole, portName)
    }

    suspend fun saveSeizure(item: PassportSeizureEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertSeizure(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("محضر ضبط جواز", "PassportSeizure", item.id, "${item.recordNumber} — ${item.travelerName} (${item.status})", officerName, officerRole, portName)
    }

    suspend fun saveMigrant(item: UndocumentedMigrantEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertMigrant(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("سجل مهاجر غير نظامي", "UndocumentedMigrant", item.id, "${item.fullName} — ${item.status}", officerName, officerRole, portName)
    }

    suspend fun saveRefugee(item: RefugeeRecordEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertRefugee(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("سجل لاجئ", "RefugeeRecord", item.id, "${item.unhcrNumber} — ${item.fullName}", officerName, officerRole, portName)
    }

    suspend fun saveForeigner(item: ForeignerResidentEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertForeigner(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("إقامة أجنبي", "ForeignerResident", item.id, "${item.residenceNumber} — ${item.fullName}", officerName, officerRole, portName)
    }

    suspend fun savePermit(item: MovementPermitEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertPermit(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("تصريح تنقل", "MovementPermit", item.id, "${item.permitCode} — ${item.delegationName}", officerName, officerRole, portName)
    }

    suspend fun saveCitizenshipCase(item: CitizenshipCaseEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertCitizenshipCase(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("قضية جنسية", "CitizenshipCase", item.id, "${item.caseNumber} — ${item.applicantName}", officerName, officerRole, portName)
    }

    suspend fun saveAgency(item: TravelAgencyEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertAgency(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("وكالة / شركة", "TravelAgency", item.id, "${item.agencyName} — ${item.clearanceStatus}", officerName, officerRole, portName)
    }

    suspend fun saveCorrespondence(item: CorrespondenceEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertCorrespondence(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("مراسلة رسمية", "CorrespondenceLetter", item.id, "${item.referenceNumber} (${item.direction}) — ${item.subject}", officerName, officerRole, portName)
    }

    suspend fun saveMofaVisa(item: MofaVisaEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertMofaVisa(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("مذكرة خارجية / رحلة", "MofaVisaRequest", item.id, "${item.memoNumber} — ${item.organization}", officerName, officerRole, portName)
    }

    suspend fun saveOfficer(item: PortOfficerEntity, officerName: String, officerRole: String, portName: String) {
        dao.upsertOfficer(item.copy(synced = false, updatedAt = System.currentTimeMillis()))
        logAudit("إدارة ضابط منفذ", "PortOfficer", item.id, "${item.rank} ${item.fullName} — ${item.role}", officerName, officerRole, portName)
    }

    suspend fun recordTravelCrossing(
        traveler: TravelerEntity,
        direction: String,
        destination: String,
        portName: String,
        officerName: String,
        officerRole: String
    ) {
        val status = TravelClearanceStatus.fromTravelAllowedFlag(traveler.travelAllowed)
        val travel = TravelEntity(
            id = "tr_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            travelerId = traveler.id,
            travelerName = traveler.fullName,
            passportNumber = traveler.idNumber,
            portName = portName,
            direction = direction,
            destination = destination,
            travelAllowed = traveler.travelAllowed,
            clearanceStatus = status,
            vettingStatus = traveler.vettingResult,
            officerName = officerName,
            flags = traveler.securityFlags,
            metadata = mapOf("source" to "traveler_detail_quick_log"),
            synced = false
        )
        dao.upsertTravel(travel)
        logAudit(
            action = "تسجيل حركة $direction",
            entityType = "Travel",
            entityId = travel.id,
            details = "${traveler.fullName} (جواز: ${traveler.idNumber}) عبر $portName — الحالة: ${status.arabicLabel}",
            officerName = officerName,
            officerRole = officerRole,
            portName = portName,
            severityLevel = if (status == TravelClearanceStatus.BLOCKED) AuditSeverity.CRITICAL else AuditSeverity.INFO,
            metadata = mapOf("passport" to traveler.idNumber, "direction" to direction)
        )
    }

    suspend fun syncWithCloud(userId: String): Result<Pair<Int, Int>> {
        val pushRes = firestoreSync.pushPendingToCloud(userId, dao)
        if (pushRes.isFailure) return Result.failure(pushRes.exceptionOrNull()!!)
        val pullRes = firestoreSync.pullCloudIntoLocal(userId, dao)
        if (pullRes.isFailure) return Result.failure(pullRes.exceptionOrNull()!!)
        return Result.success((pushRes.getOrDefault(0)) to (pullRes.getOrDefault(0)))
    }
}
