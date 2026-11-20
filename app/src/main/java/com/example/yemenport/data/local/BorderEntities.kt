package com.example.yemenport.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "travelers",
    indices = [
        Index(value = ["idNumber"]),
        Index(value = ["fullName"]),
        Index(value = ["travelAllowed"])
    ]
)
data class TravelerEntity(
    @PrimaryKey val id: String,
    val serialNumber: String = "",
    val firstName: String = "",
    val secondName: String = "",
    val thirdName: String = "",
    val fourthName: String = "",
    val lastName: String = "",
    val fullName: String,
    val passportName: String = "",
    val idNumber: String,
    val idType: String = "جواز سفر",
    val passportStatus: String = "صالح",
    val nationalId: String = "",
    val gender: String = "ذكر",
    val birthDate: String = "",
    val age: Int = 0,
    val country: String = "اليمن",
    val governorate: String = "",
    val profession: String = "",
    val phone: String = "",
    val center: String = "المركز الرئيسي - العاصمة صنعاء",
    val portName: String = "المركز الرئيسي - العاصمة صنعاء",
    val agency: String = "",
    val agencyId: String = "",
    val visaType: String = "",
    val destination: String = "",
    val visitReason: String = "",
    val receiveDate: String = "",
    val exitDate: String = "",
    val issueDate: String = "",
    val expiryDate: String = "",
    val placeOfIssue: String = "",
    val requestNumber: String = "",
    val returnReason: String = "",
    val vettingResult: String = "سليم",
    val travelAllowed: String = "نعم", // نعم | لا
    val status: String = "معتمد",
    val investigatingOfficer: String = "",
    val additionalInfo: String = "",
    val mergeHistory: String = "",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_reports")
data class SecurityReportEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetName: String,
    val targetPassportNumber: String = "",
    val reportType: String = "تعميم أمني",
    val severity: String = "عالي",
    val description: String = "",
    val assignedPort: String = "المركز الرئيسي - العاصمة صنعاء",
    val officerName: String = "",
    val status: String = "جديد", // جديد | قيد التحقيق | معتمد | مرفوض
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "patrol_movements")
data class PatrolMovementEntity(
    @PrimaryKey val id: String,
    val vehicleType: String, // طقم | دراجة نارية | أخرى
    val plateNumber: String,
    val driverName: String,
    val portName: String = "المركز الرئيسي - العاصمة صنعاء",
    val mission: String = "",
    val departureTime: String = "",
    val expectedReturnTime: String = "",
    val actualReturnTime: String = "",
    val outcomeNotes: String = "",
    val status: String = "في مهمة", // في مهمة | عاد
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "passport_seizures")
data class PassportSeizureEntity(
    @PrimaryKey val id: String,
    val recordNumber: String,
    val travelerName: String,
    val passportNumber: String,
    val nationality: String = "اليمن",
    val seizurePort: String = "المركز الرئيسي - العاصمة صنعاء",
    val seizureDate: String = "",
    val reason: String = "",
    val status: String = "جديد", // جديد | بانتظار الفحص | تم الفحص | محال | تم التسليم
    val recipientName: String = "",
    val deliveredAt: String = "",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "undocumented_migrants")
data class UndocumentedMigrantEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val nationality: String,
    val apprehensionPort: String = "",
    val apprehensionDate: String = "",
    val medicalStatus: String = "سليم",
    val shelterLocation: String = "",
    val status: String = "قيد الإيواء",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "refugees")
data class RefugeeRecordEntity(
    @PrimaryKey val id: String,
    val unhcrNumber: String,
    val fullName: String,
    val nationality: String = "",
    val campOrCity: String = "",
    val vettingStatus: String = "ساري",
    val permitExpiryDate: String = "",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "foreigner_residents")
data class ForeignerResidentEntity(
    @PrimaryKey val id: String,
    val residenceNumber: String,
    val fullName: String,
    val passportNumber: String,
    val nationality: String = "",
    val employer: String = "",
    val profession: String = "",
    val entryDate: String = "",
    val residenceExpiryDate: String = "",
    val medicalClearance: String = "لائق طبياً",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "movement_permits")
data class MovementPermitEntity(
    @PrimaryKey val id: String,
    val permitCode: String,
    val delegationName: String,
    val authorizedRoute: String = "",
    val accommodationPlace: String = "",
    val vehicleDetails: String = "",
    val passengersList: String = "",
    val passengersCount: Int = 1,
    val validFrom: String = "",
    val validTo: String = "",
    val approvalStatus: String = "معتمد رسمياً",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "citizenship_cases")
data class CitizenshipCaseEntity(
    @PrimaryKey val id: String,
    val caseNumber: String,
    val serviceType: String,
    val applicantName: String,
    val spouseOrBeneficiaryName: String = "",
    val spouseNationality: String = "",
    val submissionDate: String = "",
    val slaDaysTarget: Int = 14,
    val status: String = "قيد المراجعة",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "travel_agencies")
data class TravelAgencyEntity(
    @PrimaryKey val id: String,
    val agencyName: String,
    val companyType: String = "وكالة سفر وسياحة",
    val governorate: String = "صنعاء",
    val center: String = "",
    val clearanceStatus: String = "معتمد وساري",
    val linkedVisasCount: Int = 0,
    val issuedCount: Int = 0,
    val returnedCount: Int = 0,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "correspondence_letters")
data class CorrespondenceEntity(
    @PrimaryKey val id: String,
    val referenceNumber: String,
    val direction: String = "وارد", // صادر | وارد
    val letterType: String = "مذكرة رسمية",
    val subject: String,
    val summary: String = "",
    val senderEntity: String = "",
    val recipientEntity: String = "",
    val gregorianDate: String = "",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mofa_visas")
data class MofaVisaEntity(
    @PrimaryKey val id: String,
    val memoNumber: String,
    val memoDate: String = "",
    val organization: String,
    val requestType: String, // طلب تأشيرة دخول | مانيفست رحلة طيران | إرسالية نقل جوازات
    val flightRoute: String = "",
    val dispatchCompany: String = "",
    val beneficiariesSummary: String = "",
    val beneficiariesCount: Int = 1,
    val approvalStatus: String = "معتمد رسمياً",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "port_officers")
data class PortOfficerEntity(
    @PrimaryKey val id: String,
    val rank: String,
    val fullName: String,
    val assignedPort: String,
    val role: String = "ضابط منفذ",
    val dutyOfficer: Boolean = false,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val entityType: String,
    val entityId: String = "",
    val details: String,
    val officerName: String,
    val officerRole: String,
    val portName: String = "",
    val timestampIso: String,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_patterns")
data class LearnedPatternEntity(
    @PrimaryKey val id: String,
    val patternType: String,
    val instructionText: String,
    val keywordsCsv: String = "",
    val targetCategory: String,
    val frequency: Int = 1,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
