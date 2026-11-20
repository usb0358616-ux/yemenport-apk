package com.example.yemenport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.yemenport.core.BiometricAuthAvailability
import com.example.yemenport.core.BiometricAuthentication
import com.example.yemenport.data.local.*
import com.example.yemenport.domain.mrz.PassportOcrAnalyzer
import com.example.yemenport.domain.security.WatchlistGuardEngine
import com.example.yemenport.domain.security.WatchlistMatchSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SovereignDatabaseAndBiometricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: BorderDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.borderDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun typeConverters_roundTripComplexObjects() {
        val converters = BorderTypeConverters()
        val flags = listOf("ممنوع_من_السفر", "بلاغ_نشط")
        val metadata = mapOf("port" to "مطار عدن الدولي", "officer" to "نقيب علي")
        val merges = listOf(
            MergeHistoryEntry(
                timestampIso = "2026-11-20T06:00",
                officerName = "نقيب علي",
                portName = "منفذ الوديعة",
                changedFields = listOf("انتهاء الجواز: '2025-01-01' -> '2030-01-01'"),
                note = "تحديث ميداني"
            )
        )
        val now = Instant.ofEpochMilli(1_795_000_000_000L)

        assertEquals(flags, converters.toStringList(converters.fromStringList(flags)))
        assertEquals(metadata, converters.toStringMap(converters.fromStringMap(metadata)))
        assertEquals(merges, converters.toMergeHistoryList(converters.fromMergeHistoryList(merges)))
        assertEquals(now, converters.toInstant(converters.fromInstant(now)))
        assertEquals(
            TravelClearanceStatus.BLOCKED,
            converters.toTravelClearanceStatus(converters.fromTravelClearanceStatus(TravelClearanceStatus.BLOCKED))
        )
        assertEquals(
            AuditSeverity.CRITICAL,
            converters.toAuditSeverity(converters.fromAuditSeverity(AuditSeverity.CRITICAL))
        )
    }

    @Test
    fun roomDatabase_insertsAndReadsTravelEntityAndAuditLogEntity() = runBlocking {
        val traveler = TravelerEntity(
            id = "trv_test_1",
            fullName = "أحمد محمد صالح",
            idNumber = "08123456",
            country = "اليمن",
            portName = "منفذ الوديعة",
            securityFlags = listOf("تم_التحقق_آلياً"),
            extraAttributes = mapOf("flight" to "IY602")
        )
        dao.upsertTraveler(traveler)

        val travel = TravelEntity(
            id = "tr_test_1",
            travelerId = traveler.id,
            travelerName = traveler.fullName,
            passportNumber = traveler.idNumber,
            portName = "منفذ الوديعة",
            direction = "مغادرة",
            destination = "جدة",
            clearanceStatus = TravelClearanceStatus.ALLOWED,
            flags = listOf("تدقيق_ثنائي"),
            metadata = mapOf("lane" to "B2")
        )
        dao.upsertTravel(travel)

        val audit = AuditLogEntity(
            id = "log_test_1",
            action = "تسجيل عبور",
            entityType = "TravelEntity",
            entityId = travel.id,
            details = "عبور مسافر عبر منفذ الوديعة",
            officerName = "ملازم خالد",
            officerRole = "ضابط منفذ",
            portName = "منفذ الوديعة",
            timestampIso = Instant.now().toString(),
            severityLevel = AuditSeverity.WARN,
            metadata = mapOf("passport" to "08123456")
        )
        dao.insertAuditLog(audit)

        val loadedTraveler = dao.findTravelerByPassport("08123456")
        assertNotNull(loadedTraveler)
        assertEquals(listOf("تم_التحقق_آلياً"), loadedTraveler?.securityFlags)
        assertEquals("IY602", loadedTraveler?.extraAttributes?.get("flight"))

        val travels = dao.observeTravelsForPassport("08123456").first()
        assertEquals(1, travels.size)
        assertEquals("B2", travels.first().metadata["lane"])
        assertEquals(TravelClearanceStatus.ALLOWED, travels.first().clearanceStatus)

        val logs = dao.observeAuditLogs().first()
        assertEquals(1, logs.size)
        assertEquals("08123456", logs.first().metadata["passport"])
        assertEquals(AuditSeverity.WARN, logs.first().severityLevel)
    }

    @Test
    fun biometricAuthentication_reportsAvailabilitySafelyOnDeviceContext() {
        val helper = BiometricAuthentication()
        val status = helper.checkAvailability(context)
        assertTrue(
            status is BiometricAuthAvailability.Available ||
                status is BiometricAuthAvailability.NoHardware ||
                status is BiometricAuthAvailability.NotEnrolled ||
                status is BiometricAuthAvailability.Unavailable
        )
    }

    @Test
    fun mrzAnalyzer_parsesTd3PassportAndNormalizesArabicDigits() {
        assertEquals("1234567890", PassportOcrAnalyzer.normalizeDigits("١٢٣٤٥٦٧٨٩٠"))
        val sampleMrz = """
            P<YEMALMANSOUR<<AHMED<ALI<<<<<<<<<<<<<<<<<<<
            08123456<8YEM9001018M3012315<<<<<<<<<<<<<<04
        """.trimIndent()
        val parsed = PassportOcrAnalyzer.parseMrz(sampleMrz)
        assertNotNull(parsed)
        assertEquals("08123456", parsed?.passportNumber)
        assertEquals("YEM", parsed?.issuingCountryCode)
        assertEquals("اليمن", parsed?.nationalityArabic)

        val validation = PassportOcrAnalyzer.validateTravelerDocument(parsed!!)
        assertTrue(validation.isNotExpired)
    }

    @Test
    fun watchlistGuardEngine_detectsProhibitedTravelAndActiveWarrantsFromRoom() = runBlocking {
        dao.upsertTraveler(
            TravelerEntity(
                id = "trv_ban_1",
                fullName = "أحمد علي المنصور",
                passportName = "AHMED ALI ALMANSOUR",
                idNumber = "08123456",
                travelAllowed = "لا",
                vettingResult = "منع سفر قضائي"
            )
        )
        dao.upsertReport(
            SecurityReportEntity(
                id = "rep_war_1",
                title = "أمر قبض قهري",
                targetName = "AHMED ALI ALMANSOUR",
                targetPassportNumber = "08123456",
                reportType = "أمر قبض",
                severity = "حرج",
                status = "معتمد",
                description = "مطلوب أمنياً للمراجعة"
            )
        )

        val sampleMrz = """
            P<YEMALMANSOUR<<AHMED<ALI<<<<<<<<<<<<<<<<<<<
            08123456<8YEM9001018M3012315<<<<<<<<<<<<<<04
        """.trimIndent()
        val mrz = PassportOcrAnalyzer.parseMrz(sampleMrz)!!
        val summary = WatchlistGuardEngine.verifyExtractedMrz(mrz, dao)

        assertTrue(summary.isCritical)
        assertTrue(summary.hasActiveWarrantOrBan)
        assertTrue(summary.hits.any { it.source == WatchlistMatchSource.TRAVEL_BAN })
        assertTrue(summary.hits.any { it.source == WatchlistMatchSource.ACTIVE_WARRANT })
    }
}
