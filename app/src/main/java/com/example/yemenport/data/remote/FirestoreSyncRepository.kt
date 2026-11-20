package com.example.yemenport.data.remote

import android.content.Context
import com.example.yemenport.R
import com.example.yemenport.core.OperationType
import com.example.yemenport.core.handleFirestoreError
import com.example.yemenport.data.local.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: error("يجب تسجيل الدخول بحساب Google للمزامنة السحابية.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "تبدل المستخدم؛ رفض العملية عبر الحسابات." }
        }
        return uid
    }

    private fun userRoot(userId: String) = db.collection("users").document(userId)

    private fun sanitizeDocId(rawId: String): String {
        val clean = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return clean.ifBlank { "doc_${System.currentTimeMillis()}" }.take(120)
    }

    private suspend fun upsertDoc(
        userId: String,
        collection: String,
        docId: String,
        createMap: Map<String, Any>
    ): Result<Unit> {
        val safeId = sanitizeDocId(docId)
        val path = "users/$userId/$collection/$safeId"
        return try {
            requireUserId(userId)
            val ref = userRoot(userId).collection(collection).document(safeId)
            val snap = ref.get().await()
            if (snap.exists()) {
                val updateMap = createMap.toMutableMap().apply {
                    remove("createdAt")
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updateMap).await()
            } else {
                ref.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun createImmutableDoc(
        userId: String,
        collection: String,
        docId: String,
        createMap: Map<String, Any>
    ): Result<Unit> {
        val safeId = sanitizeDocId(docId)
        val path = "users/$userId/$collection/$safeId"
        return try {
            requireUserId(userId)
            val ref = userRoot(userId).collection(collection).document(safeId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(userId: String, collection: String, docId: String): Result<Unit> {
        val safeId = sanitizeDocId(docId)
        val path = "users/$userId/$collection/$safeId"
        return try {
            requireUserId(userId)
            userRoot(userId).collection(collection).document(safeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeProfileSettings(userId: String): Flow<FsOfficerProfileSettings?> =
        userRoot(userId).collection("settings").document("profile")
            .snapshots()
            .map { it.toObject(FsOfficerProfileSettings::class.java, ServerTimestampBehavior.ESTIMATE) }

    suspend fun saveProfileSettings(
        userId: String,
        officerName: String,
        officerRole: String,
        assignedPort: String,
        biometricEnabled: Boolean,
        highContrastMode: Boolean,
        hqCenterName: String
    ): Result<Unit> = upsertDoc(
        userId = userId,
        collection = "settings",
        docId = "profile",
        createMap = mapOf(
            "officerName" to officerName.ifBlank { "ضابط المنفذ" }.take(150),
            "officerRole" to officerRole.ifBlank { "مدير عام" }.take(80),
            "assignedPort" to assignedPort.ifBlank { "المركز الرئيسي - العاصمة صنعاء" }.take(150),
            "biometricEnabled" to biometricEnabled,
            "highContrastMode" to highContrastMode,
            "hqCenterName" to hqCenterName.take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    )

    suspend fun pushPendingToCloud(userId: String, dao: BorderDao): Result<Int> {
        return try {
            requireUserId(userId)
            var pushed = 0

            val travelers = dao.getUnsyncedTravelers()
            val syncedTravelers = mutableListOf<String>()
            for (t in travelers) {
                if (upsertDoc(userId, "travelers", t.id, t.toCreateFirestoreMap()).isSuccess) {
                    syncedTravelers.add(t.id)
                    pushed++
                }
            }
            if (syncedTravelers.isNotEmpty()) dao.markTravelersSynced(syncedTravelers)

            val reports = dao.getUnsyncedReports()
            val syncedReports = mutableListOf<String>()
            for (r in reports) {
                if (upsertDoc(userId, "reports", r.id, r.toCreateFirestoreMap()).isSuccess) {
                    syncedReports.add(r.id)
                    pushed++
                }
            }
            if (syncedReports.isNotEmpty()) dao.markReportsSynced(syncedReports)

            val movements = dao.getUnsyncedMovements()
            val syncedMovements = mutableListOf<String>()
            for (m in movements) {
                if (upsertDoc(userId, "movements", m.id, m.toCreateFirestoreMap()).isSuccess) {
                    syncedMovements.add(m.id)
                    pushed++
                }
            }
            if (syncedMovements.isNotEmpty()) dao.markMovementsSynced(syncedMovements)

            val seizures = dao.getUnsyncedSeizures()
            val syncedSeizures = mutableListOf<String>()
            for (s in seizures) {
                if (upsertDoc(userId, "seizures", s.id, s.toCreateFirestoreMap()).isSuccess) {
                    syncedSeizures.add(s.id)
                    pushed++
                }
            }
            if (syncedSeizures.isNotEmpty()) dao.markSeizuresSynced(syncedSeizures)

            val migrants = dao.getUnsyncedMigrants()
            val syncedMigrants = mutableListOf<String>()
            for (m in migrants) {
                if (upsertDoc(userId, "migrants", m.id, m.toCreateFirestoreMap()).isSuccess) {
                    syncedMigrants.add(m.id)
                    pushed++
                }
            }
            if (syncedMigrants.isNotEmpty()) dao.markMigrantsSynced(syncedMigrants)

            val refugees = dao.getUnsyncedRefugees()
            val syncedRefugees = mutableListOf<String>()
            for (r in refugees) {
                if (upsertDoc(userId, "refugees", r.id, r.toCreateFirestoreMap()).isSuccess) {
                    syncedRefugees.add(r.id)
                    pushed++
                }
            }
            if (syncedRefugees.isNotEmpty()) dao.markRefugeesSynced(syncedRefugees)

            val foreigners = dao.getUnsyncedForeigners()
            val syncedForeigners = mutableListOf<String>()
            for (f in foreigners) {
                if (upsertDoc(userId, "foreigners", f.id, f.toCreateFirestoreMap()).isSuccess) {
                    syncedForeigners.add(f.id)
                    pushed++
                }
            }
            if (syncedForeigners.isNotEmpty()) dao.markForeignersSynced(syncedForeigners)

            val permits = dao.getUnsyncedPermits()
            val syncedPermits = mutableListOf<String>()
            for (p in permits) {
                if (upsertDoc(userId, "permits", p.id, p.toCreateFirestoreMap()).isSuccess) {
                    syncedPermits.add(p.id)
                    pushed++
                }
            }
            if (syncedPermits.isNotEmpty()) dao.markPermitsSynced(syncedPermits)

            val cases = dao.getUnsyncedCitizenshipCases()
            val syncedCases = mutableListOf<String>()
            for (c in cases) {
                if (upsertDoc(userId, "citizenship_cases", c.id, c.toCreateFirestoreMap()).isSuccess) {
                    syncedCases.add(c.id)
                    pushed++
                }
            }
            if (syncedCases.isNotEmpty()) dao.markCitizenshipCasesSynced(syncedCases)

            val companies = dao.getUnsyncedAgencies()
            val syncedCompanies = mutableListOf<String>()
            for (c in companies) {
                if (upsertDoc(userId, "companies", c.id, c.toCreateFirestoreMap()).isSuccess) {
                    syncedCompanies.add(c.id)
                    pushed++
                }
            }
            if (syncedCompanies.isNotEmpty()) dao.markAgenciesSynced(syncedCompanies)

            val correspondence = dao.getUnsyncedCorrespondence()
            val syncedLetters = mutableListOf<String>()
            for (l in correspondence) {
                if (upsertDoc(userId, "correspondence", l.id, l.toCreateFirestoreMap()).isSuccess) {
                    syncedLetters.add(l.id)
                    pushed++
                }
            }
            if (syncedLetters.isNotEmpty()) dao.markCorrespondenceSynced(syncedLetters)

            val mofaVisas = dao.getUnsyncedMofaVisas()
            val syncedMofa = mutableListOf<String>()
            for (m in mofaVisas) {
                if (upsertDoc(userId, "mofa_visas", m.id, m.toCreateFirestoreMap()).isSuccess) {
                    syncedMofa.add(m.id)
                    pushed++
                }
            }
            if (syncedMofa.isNotEmpty()) dao.markMofaVisasSynced(syncedMofa)

            val officers = dao.getUnsyncedOfficers()
            val syncedOfficers = mutableListOf<String>()
            for (o in officers) {
                if (upsertDoc(userId, "officers", o.id, o.toCreateFirestoreMap()).isSuccess) {
                    syncedOfficers.add(o.id)
                    pushed++
                }
            }
            if (syncedOfficers.isNotEmpty()) dao.markOfficersSynced(syncedOfficers)

            val logs = dao.getUnsyncedAuditLogs()
            val syncedLogs = mutableListOf<String>()
            for (log in logs) {
                if (createImmutableDoc(userId, "audit_logs", log.id, log.toCreateFirestoreMap()).isSuccess) {
                    syncedLogs.add(log.id)
                    pushed++
                }
            }
            if (syncedLogs.isNotEmpty()) dao.markAuditLogsSynced(syncedLogs)

            val patterns = dao.getUnsyncedLearnedPatterns()
            val syncedPatterns = mutableListOf<String>()
            for (pt in patterns) {
                if (upsertDoc(userId, "learned_patterns", pt.id, pt.toCreateFirestoreMap()).isSuccess) {
                    syncedPatterns.add(pt.id)
                    pushed++
                }
            }
            if (syncedPatterns.isNotEmpty()) dao.markLearnedPatternsSynced(syncedPatterns)

            Result.success(pushed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun pullCloudIntoLocal(userId: String, dao: BorderDao): Result<Int> {
        return try {
            requireUserId(userId)
            var pulled = 0
            val root = userRoot(userId)

            val trvSnap = root.collection("travelers").get().await()
            val travelers = trvSnap.toObjects(FsTraveler::class.java, ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            if (travelers.isNotEmpty()) {
                dao.upsertTravelersBatch(travelers)
                pulled += travelers.size
            }

            val repSnap = root.collection("reports").get().await()
            for (item in repSnap.toObjects(FsReport::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertReport(item.toEntity())
                pulled++
            }

            val movSnap = root.collection("movements").get().await()
            for (item in movSnap.toObjects(FsMovement::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertMovement(item.toEntity())
                pulled++
            }

            val szSnap = root.collection("seizures").get().await()
            for (item in szSnap.toObjects(FsSeizure::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertSeizure(item.toEntity())
                pulled++
            }

            val migSnap = root.collection("migrants").get().await()
            for (item in migSnap.toObjects(FsMigrant::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertMigrant(item.toEntity())
                pulled++
            }

            val refSnap = root.collection("refugees").get().await()
            for (item in refSnap.toObjects(FsRefugee::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertRefugee(item.toEntity())
                pulled++
            }

            val frgSnap = root.collection("foreigners").get().await()
            val foreigners = frgSnap.toObjects(FsForeigner::class.java, ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            if (foreigners.isNotEmpty()) {
                dao.upsertForeignersBatch(foreigners)
                pulled += foreigners.size
            }

            val prmSnap = root.collection("permits").get().await()
            for (item in prmSnap.toObjects(FsPermit::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertPermit(item.toEntity())
                pulled++
            }

            val citSnap = root.collection("citizenship_cases").get().await()
            for (item in citSnap.toObjects(FsCitizenshipCase::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertCitizenshipCase(item.toEntity())
                pulled++
            }

            val cmpSnap = root.collection("companies").get().await()
            val companies = cmpSnap.toObjects(FsCompany::class.java, ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            if (companies.isNotEmpty()) {
                dao.upsertAgenciesBatch(companies)
                pulled += companies.size
            }

            val corSnap = root.collection("correspondence").get().await()
            for (item in corSnap.toObjects(FsCorrespondence::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertCorrespondence(item.toEntity())
                pulled++
            }

            val mofaSnap = root.collection("mofa_visas").get().await()
            for (item in mofaSnap.toObjects(FsMofaVisa::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertMofaVisa(item.toEntity())
                pulled++
            }

            val offSnap = root.collection("officers").get().await()
            for (item in offSnap.toObjects(FsPortOfficer::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertOfficer(item.toEntity())
                pulled++
            }

            val logSnap = root.collection("audit_logs").get().await()
            for (item in logSnap.toObjects(FsAuditLog::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.insertAuditLog(item.toEntity())
                pulled++
            }

            val patSnap = root.collection("learned_patterns").get().await()
            for (item in patSnap.toObjects(FsLearnedPattern::class.java, ServerTimestampBehavior.ESTIMATE)) {
                dao.upsertLearnedPattern(item.toEntity())
                pulled++
            }

            Result.success(pulled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId")
            Result.failure(e)
        }
    }
}
