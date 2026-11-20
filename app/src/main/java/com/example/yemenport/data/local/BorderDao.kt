package com.example.yemenport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BorderDao {

    // Travelers
    @Query("SELECT * FROM travelers ORDER BY createdAt DESC LIMIT 1000")
    fun observeTravelers(): Flow<List<TravelerEntity>>

    @Query("SELECT * FROM travelers ORDER BY createdAt DESC")
    suspend fun getAllTravelers(): List<TravelerEntity>

    @Query("SELECT * FROM travelers WHERE idNumber = :passportNo LIMIT 1")
    suspend fun findTravelerByPassport(passportNo: String): TravelerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTraveler(item: TravelerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTravelersBatch(items: List<TravelerEntity>)

    @Query("DELETE FROM travelers WHERE id = :id")
    suspend fun deleteTraveler(id: String)

    @Query("SELECT * FROM travelers WHERE synced = 0 LIMIT 200")
    suspend fun getUnsyncedTravelers(): List<TravelerEntity>

    @Query("UPDATE travelers SET synced = 1 WHERE id IN (:ids)")
    suspend fun markTravelersSynced(ids: List<String>)

    // Security Reports
    @Query("SELECT * FROM security_reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<SecurityReportEntity>>

    @Query("SELECT * FROM security_reports ORDER BY createdAt DESC")
    suspend fun getAllReports(): List<SecurityReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(item: SecurityReportEntity)

    @Query("DELETE FROM security_reports WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("SELECT * FROM security_reports WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedReports(): List<SecurityReportEntity>

    @Query("UPDATE security_reports SET synced = 1 WHERE id IN (:ids)")
    suspend fun markReportsSynced(ids: List<String>)

    // Patrol Movements
    @Query("SELECT * FROM patrol_movements ORDER BY createdAt DESC")
    fun observeMovements(): Flow<List<PatrolMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovement(item: PatrolMovementEntity)

    @Query("DELETE FROM patrol_movements WHERE id = :id")
    suspend fun deleteMovement(id: String)

    @Query("SELECT * FROM patrol_movements WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedMovements(): List<PatrolMovementEntity>

    @Query("UPDATE patrol_movements SET synced = 1 WHERE id IN (:ids)")
    suspend fun markMovementsSynced(ids: List<String>)

    // Passport Seizures
    @Query("SELECT * FROM passport_seizures ORDER BY createdAt DESC")
    fun observeSeizures(): Flow<List<PassportSeizureEntity>>

    @Query("SELECT * FROM passport_seizures ORDER BY createdAt DESC")
    suspend fun getAllSeizures(): List<PassportSeizureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeizure(item: PassportSeizureEntity)

    @Query("SELECT * FROM passport_seizures WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedSeizures(): List<PassportSeizureEntity>

    @Query("UPDATE passport_seizures SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSeizuresSynced(ids: List<String>)

    // Undocumented Migrants
    @Query("SELECT * FROM undocumented_migrants ORDER BY createdAt DESC")
    fun observeMigrants(): Flow<List<UndocumentedMigrantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMigrant(item: UndocumentedMigrantEntity)

    @Query("SELECT * FROM undocumented_migrants WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedMigrants(): List<UndocumentedMigrantEntity>

    @Query("UPDATE undocumented_migrants SET synced = 1 WHERE id IN (:ids)")
    suspend fun markMigrantsSynced(ids: List<String>)

    // Refugees
    @Query("SELECT * FROM refugees ORDER BY createdAt DESC")
    fun observeRefugees(): Flow<List<RefugeeRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRefugee(item: RefugeeRecordEntity)

    @Query("SELECT * FROM refugees WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedRefugees(): List<RefugeeRecordEntity>

    @Query("UPDATE refugees SET synced = 1 WHERE id IN (:ids)")
    suspend fun markRefugeesSynced(ids: List<String>)

    // Foreigner Residents
    @Query("SELECT * FROM foreigner_residents ORDER BY createdAt DESC")
    fun observeForeigners(): Flow<List<ForeignerResidentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForeigner(item: ForeignerResidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForeignersBatch(items: List<ForeignerResidentEntity>)

    @Query("SELECT * FROM foreigner_residents WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedForeigners(): List<ForeignerResidentEntity>

    @Query("UPDATE foreigner_residents SET synced = 1 WHERE id IN (:ids)")
    suspend fun markForeignersSynced(ids: List<String>)

    // Movement Permits
    @Query("SELECT * FROM movement_permits ORDER BY createdAt DESC")
    fun observePermits(): Flow<List<MovementPermitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPermit(item: MovementPermitEntity)

    @Query("SELECT * FROM movement_permits WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedPermits(): List<MovementPermitEntity>

    @Query("UPDATE movement_permits SET synced = 1 WHERE id IN (:ids)")
    suspend fun markPermitsSynced(ids: List<String>)

    // Citizenship Cases
    @Query("SELECT * FROM citizenship_cases ORDER BY createdAt DESC")
    fun observeCitizenshipCases(): Flow<List<CitizenshipCaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCitizenshipCase(item: CitizenshipCaseEntity)

    @Query("SELECT * FROM citizenship_cases WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedCitizenshipCases(): List<CitizenshipCaseEntity>

    @Query("UPDATE citizenship_cases SET synced = 1 WHERE id IN (:ids)")
    suspend fun markCitizenshipCasesSynced(ids: List<String>)

    // Travel Agencies & Monitored Companies
    @Query("SELECT * FROM travel_agencies ORDER BY linkedVisasCount DESC, agencyName ASC")
    fun observeAgencies(): Flow<List<TravelAgencyEntity>>

    @Query("SELECT * FROM travel_agencies")
    suspend fun getAllAgencies(): List<TravelAgencyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgency(item: TravelAgencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgenciesBatch(items: List<TravelAgencyEntity>)

    @Query("SELECT * FROM travel_agencies WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedAgencies(): List<TravelAgencyEntity>

    @Query("UPDATE travel_agencies SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAgenciesSynced(ids: List<String>)

    // Correspondence
    @Query("SELECT * FROM correspondence_letters ORDER BY createdAt DESC")
    fun observeCorrespondence(): Flow<List<CorrespondenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCorrespondence(item: CorrespondenceEntity)

    @Query("SELECT * FROM correspondence_letters WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedCorrespondence(): List<CorrespondenceEntity>

    @Query("UPDATE correspondence_letters SET synced = 1 WHERE id IN (:ids)")
    suspend fun markCorrespondenceSynced(ids: List<String>)

    // MOFA Visas & Flight Manifests
    @Query("SELECT * FROM mofa_visas ORDER BY createdAt DESC")
    fun observeMofaVisas(): Flow<List<MofaVisaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMofaVisa(item: MofaVisaEntity)

    @Query("SELECT * FROM mofa_visas WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedMofaVisas(): List<MofaVisaEntity>

    @Query("UPDATE mofa_visas SET synced = 1 WHERE id IN (:ids)")
    suspend fun markMofaVisasSynced(ids: List<String>)

    // Port Officers
    @Query("SELECT * FROM port_officers ORDER BY dutyOfficer DESC, fullName ASC")
    fun observeOfficers(): Flow<List<PortOfficerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfficer(item: PortOfficerEntity)

    @Query("SELECT * FROM port_officers WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedOfficers(): List<PortOfficerEntity>

    @Query("UPDATE port_officers SET synced = 1 WHERE id IN (:ids)")
    suspend fun markOfficersSynced(ids: List<String>)

    // Immutable Audit Logs (Insert only — no update/delete methods exposed to domain)
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 500")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuditLog(item: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedAuditLogs(): List<AuditLogEntity>

    @Query("UPDATE audit_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAuditLogsSynced(ids: List<String>)

    // Learned Patterns
    @Query("SELECT * FROM learned_patterns ORDER BY frequency DESC, updatedAt DESC")
    fun observeLearnedPatterns(): Flow<List<LearnedPatternEntity>>

    @Query("SELECT * FROM learned_patterns ORDER BY frequency DESC")
    suspend fun getAllLearnedPatterns(): List<LearnedPatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearnedPattern(item: LearnedPatternEntity)

    @Query("DELETE FROM learned_patterns WHERE id = :id")
    suspend fun deleteLearnedPattern(id: String)

    @Query("SELECT * FROM learned_patterns WHERE synced = 0 LIMIT 100")
    suspend fun getUnsyncedLearnedPatterns(): List<LearnedPatternEntity>

    @Query("UPDATE learned_patterns SET synced = 1 WHERE id IN (:ids)")
    suspend fun markLearnedPatternsSynced(ids: List<String>)
}
