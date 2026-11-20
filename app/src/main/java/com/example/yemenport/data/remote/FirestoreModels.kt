package com.example.yemenport.data.remote

import androidx.annotation.Keep
import com.example.yemenport.data.local.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

private fun mapWithoutNulls(vararg pairs: Pair<String, Any?>): Map<String, Any> {
    val out = LinkedHashMap<String, Any>(pairs.size)
    for ((k, v) in pairs) {
        if (v != null) out[k] = v
    }
    return out
}

@Keep
@IgnoreExtraProperties
data class FsTraveler(
    @DocumentId val id: String = "",
    val serialNumber: String = "",
    val firstName: String = "",
    val secondName: String = "",
    val thirdName: String = "",
    val fourthName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val passportName: String = "",
    val idNumber: String = "",
    val idType: String = "جواز سفر",
    val passportStatus: String = "صالح",
    val nationalId: String = "",
    val gender: String = "ذكر",
    val birthDate: String = "",
    val age: Long = 0L,
    val country: String = "اليمن",
    val governorate: String = "",
    val profession: String = "",
    val phone: String = "",
    val center: String = "",
    val portName: String = "",
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
    val travelAllowed: String = "نعم",
    val status: String = "معتمد",
    val investigatingOfficer: String = "",
    val additionalInfo: String = "",
    val mergeHistory: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun TravelerEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "serialNumber" to serialNumber.take(40),
    "firstName" to firstName.take(60),
    "secondName" to secondName.take(60),
    "thirdName" to thirdName.take(60),
    "fourthName" to fourthName.take(60),
    "lastName" to lastName.take(60),
    "fullName" to fullName.ifBlank { "غير محدد" }.take(200),
    "passportName" to passportName.take(200),
    "idNumber" to idNumber.take(80),
    "idType" to idType.take(60),
    "passportStatus" to passportStatus.take(60),
    "nationalId" to nationalId.take(60),
    "gender" to gender.take(30),
    "birthDate" to birthDate.take(40),
    "age" to age.coerceIn(0, 150),
    "country" to country.take(100),
    "governorate" to governorate.take(100),
    "profession" to profession.take(120),
    "phone" to phone.take(40),
    "center" to center.take(150),
    "portName" to portName.take(150),
    "agency" to agency.take(150),
    "agencyId" to agencyId.take(128),
    "visaType" to visaType.take(100),
    "destination" to destination.take(120),
    "visitReason" to visitReason.take(200),
    "receiveDate" to receiveDate.take(40),
    "exitDate" to exitDate.take(40),
    "issueDate" to issueDate.take(40),
    "expiryDate" to expiryDate.take(40),
    "placeOfIssue" to placeOfIssue.take(120),
    "requestNumber" to requestNumber.take(80),
    "returnReason" to returnReason.take(300),
    "vettingResult" to vettingResult.take(200),
    "travelAllowed" to if (travelAllowed == "لا") "لا" else "نعم",
    "status" to status.ifBlank { "معتمد" }.take(60),
    "investigatingOfficer" to investigatingOfficer.take(150),
    "additionalInfo" to additionalInfo.take(1000),
    "mergeHistory" to mergeHistory.take(2000),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsTraveler.toEntity(): TravelerEntity = TravelerEntity(
    id = id,
    serialNumber = serialNumber,
    firstName = firstName,
    secondName = secondName,
    thirdName = thirdName,
    fourthName = fourthName,
    lastName = lastName,
    fullName = fullName,
    passportName = passportName,
    idNumber = idNumber,
    idType = idType,
    passportStatus = passportStatus,
    nationalId = nationalId,
    gender = gender,
    birthDate = birthDate,
    age = age.toInt(),
    country = country,
    governorate = governorate,
    profession = profession,
    phone = phone,
    center = center,
    portName = portName,
    agency = agency,
    agencyId = agencyId,
    visaType = visaType,
    destination = destination,
    visitReason = visitReason,
    receiveDate = receiveDate,
    exitDate = exitDate,
    issueDate = issueDate,
    expiryDate = expiryDate,
    placeOfIssue = placeOfIssue,
    requestNumber = requestNumber,
    returnReason = returnReason,
    vettingResult = vettingResult,
    travelAllowed = travelAllowed,
    status = status,
    investigatingOfficer = investigatingOfficer,
    additionalInfo = additionalInfo,
    mergeHistory = mergeHistory,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsReport(
    @DocumentId val id: String = "",
    val title: String = "",
    val targetName: String = "",
    val targetPassportNumber: String = "",
    val reportType: String = "",
    val severity: String = "",
    val description: String = "",
    val assignedPort: String = "",
    val officerName: String = "",
    val status: String = "جديد",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun SecurityReportEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "title" to title.ifBlank { "بلاغ أمني" }.take(200),
    "targetName" to targetName.take(200),
    "targetPassportNumber" to targetPassportNumber.take(80),
    "reportType" to reportType.take(80),
    "severity" to severity.take(40),
    "description" to description.take(2000),
    "assignedPort" to assignedPort.take(150),
    "officerName" to officerName.take(150),
    "status" to status.ifBlank { "جديد" }.take(60),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsReport.toEntity(): SecurityReportEntity = SecurityReportEntity(
    id = id,
    title = title,
    targetName = targetName,
    targetPassportNumber = targetPassportNumber,
    reportType = reportType,
    severity = severity,
    description = description,
    assignedPort = assignedPort,
    officerName = officerName,
    status = status,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsMovement(
    @DocumentId val id: String = "",
    val vehicleType: String = "",
    val plateNumber: String = "",
    val driverName: String = "",
    val portName: String = "",
    val mission: String = "",
    val departureTime: String = "",
    val expectedReturnTime: String = "",
    val actualReturnTime: String = "",
    val outcomeNotes: String = "",
    val status: String = "في مهمة",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun PatrolMovementEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "vehicleType" to vehicleType.ifBlank { "طقم" }.take(60),
    "plateNumber" to plateNumber.ifBlank { "غير محدد" }.take(60),
    "driverName" to driverName.ifBlank { "غير محدد" }.take(150),
    "portName" to portName.take(150),
    "mission" to mission.take(400),
    "departureTime" to departureTime.take(60),
    "expectedReturnTime" to expectedReturnTime.take(60),
    "actualReturnTime" to actualReturnTime.take(60),
    "outcomeNotes" to outcomeNotes.take(500),
    "status" to status.ifBlank { "في مهمة" }.take(60),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsMovement.toEntity(): PatrolMovementEntity = PatrolMovementEntity(
    id = id,
    vehicleType = vehicleType,
    plateNumber = plateNumber,
    driverName = driverName,
    portName = portName,
    mission = mission,
    departureTime = departureTime,
    expectedReturnTime = expectedReturnTime,
    actualReturnTime = actualReturnTime,
    outcomeNotes = outcomeNotes,
    status = status,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsSeizure(
    @DocumentId val id: String = "",
    val recordNumber: String = "",
    val travelerName: String = "",
    val passportNumber: String = "",
    val nationality: String = "",
    val seizurePort: String = "",
    val seizureDate: String = "",
    val reason: String = "",
    val status: String = "جديد",
    val recipientName: String = "",
    val deliveredAt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun PassportSeizureEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "recordNumber" to recordNumber.ifBlank { "SZ-001" }.take(80),
    "travelerName" to travelerName.ifBlank { "غير محدد" }.take(200),
    "passportNumber" to passportNumber.take(80),
    "nationality" to nationality.take(100),
    "seizurePort" to seizurePort.take(150),
    "seizureDate" to seizureDate.take(40),
    "reason" to reason.take(400),
    "status" to status.ifBlank { "جديد" }.take(60),
    "recipientName" to recipientName.take(150),
    "deliveredAt" to deliveredAt.take(60),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsSeizure.toEntity(): PassportSeizureEntity = PassportSeizureEntity(
    id = id,
    recordNumber = recordNumber,
    travelerName = travelerName,
    passportNumber = passportNumber,
    nationality = nationality,
    seizurePort = seizurePort,
    seizureDate = seizureDate,
    reason = reason,
    status = status,
    recipientName = recipientName,
    deliveredAt = deliveredAt,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsMigrant(
    @DocumentId val id: String = "",
    val fullName: String = "",
    val nationality: String = "",
    val apprehensionPort: String = "",
    val apprehensionDate: String = "",
    val medicalStatus: String = "",
    val shelterLocation: String = "",
    val status: String = "قيد الإيواء",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun UndocumentedMigrantEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "fullName" to fullName.ifBlank { "غير محدد" }.take(200),
    "nationality" to nationality.ifBlank { "غير محددة" }.take(100),
    "apprehensionPort" to apprehensionPort.take(150),
    "apprehensionDate" to apprehensionDate.take(40),
    "medicalStatus" to medicalStatus.take(120),
    "shelterLocation" to shelterLocation.take(200),
    "status" to status.ifBlank { "قيد الإيواء" }.take(80),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsMigrant.toEntity(): UndocumentedMigrantEntity = UndocumentedMigrantEntity(
    id = id,
    fullName = fullName,
    nationality = nationality,
    apprehensionPort = apprehensionPort,
    apprehensionDate = apprehensionDate,
    medicalStatus = medicalStatus,
    shelterLocation = shelterLocation,
    status = status,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsRefugee(
    @DocumentId val id: String = "",
    val unhcrNumber: String = "",
    val fullName: String = "",
    val nationality: String = "",
    val campOrCity: String = "",
    val vettingStatus: String = "ساري",
    val permitExpiryDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun RefugeeRecordEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "unhcrNumber" to unhcrNumber.ifBlank { "UNHCR-001" }.take(80),
    "fullName" to fullName.ifBlank { "غير محدد" }.take(200),
    "nationality" to nationality.take(100),
    "campOrCity" to campOrCity.take(150),
    "vettingStatus" to vettingStatus.ifBlank { "ساري" }.take(80),
    "permitExpiryDate" to permitExpiryDate.take(40),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsRefugee.toEntity(): RefugeeRecordEntity = RefugeeRecordEntity(
    id = id,
    unhcrNumber = unhcrNumber,
    fullName = fullName,
    nationality = nationality,
    campOrCity = campOrCity,
    vettingStatus = vettingStatus,
    permitExpiryDate = permitExpiryDate,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsForeigner(
    @DocumentId val id: String = "",
    val residenceNumber: String = "",
    val fullName: String = "",
    val passportNumber: String = "",
    val nationality: String = "",
    val employer: String = "",
    val profession: String = "",
    val entryDate: String = "",
    val residenceExpiryDate: String = "",
    val medicalClearance: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun ForeignerResidentEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "residenceNumber" to residenceNumber.ifBlank { "RES-001" }.take(80),
    "fullName" to fullName.ifBlank { "غير محدد" }.take(200),
    "passportNumber" to passportNumber.take(80),
    "nationality" to nationality.take(100),
    "employer" to employer.take(200),
    "profession" to profession.take(120),
    "entryDate" to entryDate.take(40),
    "residenceExpiryDate" to residenceExpiryDate.take(40),
    "medicalClearance" to medicalClearance.take(100),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsForeigner.toEntity(): ForeignerResidentEntity = ForeignerResidentEntity(
    id = id,
    residenceNumber = residenceNumber,
    fullName = fullName,
    passportNumber = passportNumber,
    nationality = nationality,
    employer = employer,
    profession = profession,
    entryDate = entryDate,
    residenceExpiryDate = residenceExpiryDate,
    medicalClearance = medicalClearance,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsPermit(
    @DocumentId val id: String = "",
    val permitCode: String = "",
    val delegationName: String = "",
    val authorizedRoute: String = "",
    val accommodationPlace: String = "",
    val vehicleDetails: String = "",
    val passengersList: String = "",
    val passengersCount: Long = 1L,
    val validFrom: String = "",
    val validTo: String = "",
    val approvalStatus: String = "معتمد رسمياً",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun MovementPermitEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "permitCode" to permitCode.ifBlank { "PRM-001" }.take(80),
    "delegationName" to delegationName.ifBlank { "وفد رسمي" }.take(200),
    "authorizedRoute" to authorizedRoute.take(300),
    "accommodationPlace" to accommodationPlace.take(200),
    "vehicleDetails" to vehicleDetails.take(300),
    "passengersList" to passengersList.take(2000),
    "passengersCount" to passengersCount.coerceIn(1, 5000),
    "validFrom" to validFrom.take(40),
    "validTo" to validTo.take(40),
    "approvalStatus" to approvalStatus.ifBlank { "معتمد رسمياً" }.take(80),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsPermit.toEntity(): MovementPermitEntity = MovementPermitEntity(
    id = id,
    permitCode = permitCode,
    delegationName = delegationName,
    authorizedRoute = authorizedRoute,
    accommodationPlace = accommodationPlace,
    vehicleDetails = vehicleDetails,
    passengersList = passengersList,
    passengersCount = passengersCount.toInt().coerceAtLeast(1),
    validFrom = validFrom,
    validTo = validTo,
    approvalStatus = approvalStatus,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsCitizenshipCase(
    @DocumentId val id: String = "",
    val caseNumber: String = "",
    val serviceType: String = "",
    val applicantName: String = "",
    val spouseOrBeneficiaryName: String = "",
    val spouseNationality: String = "",
    val submissionDate: String = "",
    val slaDaysTarget: Long = 14L,
    val status: String = "قيد المراجعة",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun CitizenshipCaseEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "caseNumber" to caseNumber.ifBlank { "CIT-001" }.take(80),
    "serviceType" to serviceType.ifBlank { "طلب جنسية" }.take(150),
    "applicantName" to applicantName.ifBlank { "غير محدد" }.take(200),
    "spouseOrBeneficiaryName" to spouseOrBeneficiaryName.take(200),
    "spouseNationality" to spouseNationality.take(100),
    "submissionDate" to submissionDate.take(40),
    "slaDaysTarget" to slaDaysTarget.coerceIn(1, 3650),
    "status" to status.ifBlank { "قيد المراجعة" }.take(80),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsCitizenshipCase.toEntity(): CitizenshipCaseEntity = CitizenshipCaseEntity(
    id = id,
    caseNumber = caseNumber,
    serviceType = serviceType,
    applicantName = applicantName,
    spouseOrBeneficiaryName = spouseOrBeneficiaryName,
    spouseNationality = spouseNationality,
    submissionDate = submissionDate,
    slaDaysTarget = slaDaysTarget.toInt().coerceAtLeast(1),
    status = status,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsCompany(
    @DocumentId val id: String = "",
    val agencyName: String = "",
    val companyType: String = "",
    val governorate: String = "",
    val center: String = "",
    val clearanceStatus: String = "معتمد وساري",
    val linkedVisasCount: Long = 0L,
    val issuedCount: Long = 0L,
    val returnedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun TravelAgencyEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "agencyName" to agencyName.ifBlank { "وكالة سفر" }.take(200),
    "companyType" to companyType.take(100),
    "governorate" to governorate.take(100),
    "center" to center.take(150),
    "clearanceStatus" to clearanceStatus.ifBlank { "معتمد وساري" }.take(80),
    "linkedVisasCount" to linkedVisasCount.coerceIn(0, 1000000),
    "issuedCount" to issuedCount.coerceIn(0, 1000000),
    "returnedCount" to returnedCount.coerceIn(0, 1000000),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsCompany.toEntity(): TravelAgencyEntity = TravelAgencyEntity(
    id = id,
    agencyName = agencyName,
    companyType = companyType,
    governorate = governorate,
    center = center,
    clearanceStatus = clearanceStatus,
    linkedVisasCount = linkedVisasCount.toInt(),
    issuedCount = issuedCount.toInt(),
    returnedCount = returnedCount.toInt(),
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsCorrespondence(
    @DocumentId val id: String = "",
    val referenceNumber: String = "",
    val direction: String = "وارد",
    val letterType: String = "",
    val subject: String = "",
    val summary: String = "",
    val senderEntity: String = "",
    val recipientEntity: String = "",
    val gregorianDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun CorrespondenceEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "referenceNumber" to referenceNumber.ifBlank { "DOC-001" }.take(80),
    "direction" to direction.ifBlank { "وارد" }.take(40),
    "letterType" to letterType.take(100),
    "subject" to subject.ifBlank { "مذكرة رسمية" }.take(300),
    "summary" to summary.take(2000),
    "senderEntity" to senderEntity.take(200),
    "recipientEntity" to recipientEntity.take(200),
    "gregorianDate" to gregorianDate.take(40),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsCorrespondence.toEntity(): CorrespondenceEntity = CorrespondenceEntity(
    id = id,
    referenceNumber = referenceNumber,
    direction = direction,
    letterType = letterType,
    subject = subject,
    summary = summary,
    senderEntity = senderEntity,
    recipientEntity = recipientEntity,
    gregorianDate = gregorianDate,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsMofaVisa(
    @DocumentId val id: String = "",
    val memoNumber: String = "",
    val memoDate: String = "",
    val organization: String = "",
    val requestType: String = "",
    val flightRoute: String = "",
    val dispatchCompany: String = "",
    val beneficiariesSummary: String = "",
    val beneficiariesCount: Long = 1L,
    val approvalStatus: String = "معتمد رسمياً",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun MofaVisaEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "memoNumber" to memoNumber.ifBlank { "MOFA-001" }.take(80),
    "memoDate" to memoDate.take(40),
    "organization" to organization.ifBlank { "منظمة دولية" }.take(200),
    "requestType" to requestType.ifBlank { "طلب تأشيرة دخول" }.take(100),
    "flightRoute" to flightRoute.take(200),
    "dispatchCompany" to dispatchCompany.take(150),
    "beneficiariesSummary" to beneficiariesSummary.take(2000),
    "beneficiariesCount" to beneficiariesCount.coerceIn(0, 10000),
    "approvalStatus" to approvalStatus.ifBlank { "معتمد رسمياً" }.take(80),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsMofaVisa.toEntity(): MofaVisaEntity = MofaVisaEntity(
    id = id,
    memoNumber = memoNumber,
    memoDate = memoDate,
    organization = organization,
    requestType = requestType,
    flightRoute = flightRoute,
    dispatchCompany = dispatchCompany,
    beneficiariesSummary = beneficiariesSummary,
    beneficiariesCount = beneficiariesCount.toInt(),
    approvalStatus = approvalStatus,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsPortOfficer(
    @DocumentId val id: String = "",
    val rank: String = "",
    val fullName: String = "",
    val assignedPort: String = "",
    val role: String = "ضابط منفذ",
    val dutyOfficer: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun PortOfficerEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "rank" to rank.ifBlank { "نقيب" }.take(80),
    "fullName" to fullName.ifBlank { "ضابط" }.take(200),
    "assignedPort" to assignedPort.ifBlank { "المركز الرئيسي - العاصمة صنعاء" }.take(150),
    "role" to role.ifBlank { "ضابط منفذ" }.take(80),
    "dutyOfficer" to dutyOfficer,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsPortOfficer.toEntity(): PortOfficerEntity = PortOfficerEntity(
    id = id,
    rank = rank,
    fullName = fullName,
    assignedPort = assignedPort,
    role = role,
    dutyOfficer = dutyOfficer,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsAuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val details: String = "",
    val officerName: String = "",
    val officerRole: String = "",
    val portName: String = "",
    val timestampIso: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun AuditLogEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "action" to action.ifBlank { "إجراء" }.take(100),
    "entityType" to entityType.ifBlank { "System" }.take(100),
    "entityId" to entityId.take(128),
    "details" to details.ifBlank { "-" }.take(1000),
    "officerName" to officerName.ifBlank { "النظام" }.take(150),
    "officerRole" to officerRole.ifBlank { "ضابط منفذ" }.take(80),
    "portName" to portName.take(150),
    "timestampIso" to timestampIso.take(60),
    "createdAt" to FieldValue.serverTimestamp()
)

fun FsAuditLog.toEntity(): AuditLogEntity = AuditLogEntity(
    id = id,
    action = action,
    entityType = entityType,
    entityId = entityId,
    details = details,
    officerName = officerName,
    officerRole = officerRole,
    portName = portName,
    timestampIso = timestampIso,
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsLearnedPattern(
    @DocumentId val id: String = "",
    val patternType: String = "",
    val instructionText: String = "",
    val keywordsCsv: String = "",
    val targetCategory: String = "",
    val frequency: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun LearnedPatternEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "patternType" to patternType.ifBlank { "column_signature" }.take(80),
    "instructionText" to instructionText.ifBlank { "قاعدة تصنيف" }.take(500),
    "keywordsCsv" to keywordsCsv.take(500),
    "targetCategory" to targetCategory.ifBlank { "سجل المسافرين" }.take(150),
    "frequency" to frequency.coerceIn(1, 1000000),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsLearnedPattern.toEntity(): LearnedPatternEntity = LearnedPatternEntity(
    id = id,
    patternType = patternType,
    instructionText = instructionText,
    keywordsCsv = keywordsCsv,
    targetCategory = targetCategory,
    frequency = frequency.toInt().coerceAtLeast(1),
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    updatedAt = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class FsOfficerProfileSettings(
    @DocumentId val id: String = "",
    val officerName: String = "",
    val officerRole: String = "مدير عام",
    val assignedPort: String = "المركز الرئيسي - العاصمة صنعاء",
    val biometricEnabled: Boolean = false,
    val highContrastMode: Boolean = false,
    val hqCenterName: String = "مصلحة الهجرة والجوازات - المركز الرئيسي صنعاء",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsTravel(
    @DocumentId val id: String = "",
    val travelerId: String = "",
    val travelerName: String = "",
    val passportNumber: String = "",
    val portName: String = "",
    val direction: String = "مغادرة",
    val destination: String = "",
    val travelAllowed: String = "نعم",
    val clearanceStatus: String = "ALLOWED",
    val vettingStatus: String = "سليم",
    val officerName: String = "",
    val crossedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun TravelEntity.toCreateFirestoreMap(): Map<String, Any> = mapWithoutNulls(
    "travelerId" to travelerId.ifBlank { id }.take(128),
    "travelerName" to travelerName.ifBlank { "مسافر" }.take(200),
    "passportNumber" to passportNumber.take(80),
    "portName" to portName.ifBlank { "المركز الرئيسي - العاصمة صنعاء" }.take(150),
    "direction" to direction.ifBlank { "عبور" }.take(40),
    "destination" to destination.take(120),
    "travelAllowed" to if (travelAllowed == "لا") "لا" else "نعم",
    "clearanceStatus" to clearanceStatus.name,
    "vettingStatus" to vettingStatus.take(150),
    "officerName" to officerName.take(150),
    "crossedAtEpochMs" to crossedAt.toEpochMilli(),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)

fun FsTravel.toEntity(): TravelEntity = TravelEntity(
    id = id,
    travelerId = travelerId,
    travelerName = travelerName,
    passportNumber = passportNumber,
    portName = portName,
    direction = direction,
    destination = destination,
    travelAllowed = travelAllowed,
    clearanceStatus = TravelClearanceStatus.fromCode(clearanceStatus),
    vettingStatus = vettingStatus,
    officerName = officerName,
    crossedAt = if (crossedAtEpochMs > 0L) java.time.Instant.ofEpochMilli(crossedAtEpochMs) else java.time.Instant.now(),
    synced = true,
    createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
)
