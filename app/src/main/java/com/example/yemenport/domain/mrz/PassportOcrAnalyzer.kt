package com.example.yemenport.domain.mrz

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.yemenport.core.SovereignConstants
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

data class MrzParseResult(
    val format: String = "OCR",
    val passportNumber: String = "",
    val passportCheckValid: Boolean = false,
    val surname: String = "",
    val givenNames: String = "",
    val fullLatinName: String = "",
    val issuingCountryCode: String = "",
    val nationalityArabic: String = "اليمن",
    val gender: String = "ذكر",
    val birthDate: String = "",
    val birthCheckValid: Boolean = false,
    val age: Int = 0,
    val expiryDate: String = "",
    val expiryCheckValid: Boolean = false,
    val passportStatus: String = "صالح",
    val compositeCheckValid: Boolean = false,
    val rawMrzLines: List<String> = emptyList(),
    val rawOcrText: String = ""
) {
    val allChecksPassed: Boolean
        get() = passportCheckValid && birthCheckValid && expiryCheckValid
}

data class TravelerDocumentValidation(
    val isPassportValid: Boolean,
    val isCheckDigitValid: Boolean,
    val isNotExpired: Boolean,
    val issues: List<String>
)

/**
 * CameraX ImageAnalysis.Analyzer backed by Google ML Kit Text Recognition.
 * Processes live camera frames, extracts ICAO TD3 / TD1 MRZ fields (passport number,
 * holder name, nationality, gender, birth date, expiry date), and validates check digits.
 */
class PassportOcrAnalyzer(
    private val minFrameIntervalMs: Long = 450L,
    private val onMrzDetected: (MrzParseResult) -> Unit = {},
    private val onTextObserved: (String) -> Unit = {},
    private val onError: (Throwable) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var lastProcessedMs: Long = 0L

    @Volatile
    private var locked: Boolean = false

    fun resetLock() {
        locked = false
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (locked || now - lastProcessedMs < minFrameIntervalMs) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        lastProcessedMs = now
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                if (text.isNotBlank()) {
                    onTextObserved(text)
                    val mrz = parseMrz(text)
                    if (mrz != null && mrz.passportNumber.isNotBlank()) {
                        locked = true
                        onMrzDetected(mrz)
                    }
                }
            }
            .addOnFailureListener { exc ->
                onError(exc)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    companion object {
        fun normalizeDigits(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                    in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun computeIcaoCheckDigit(field: String): Int {
            val weights = intArrayOf(7, 3, 1)
            var sum = 0
            for (i in field.indices) {
                val ch = field[i]
                val value = when {
                    ch == '<' -> 0
                    ch in '0'..'9' -> ch - '0'
                    ch in 'A'..'Z' -> ch - 'A' + 10
                    else -> 0
                }
                sum += value * weights[i % 3]
            }
            return sum % 10
        }

        private fun verifyCheckDigit(field: String, checkChar: Char): Boolean {
            val expected = computeIcaoCheckDigit(field)
            val actual = checkChar.digitToIntOrNull() ?: return false
            return expected == actual
        }

        private fun cleanMrzNumberChunk(raw: String): String {
            return raw.uppercase()
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1')
                .replace('S', '5')
                .replace('B', '8')
        }

        private fun parseYyMmDd(raw: String, isBirthDate: Boolean): String {
            val clean = cleanMrzNumberChunk(raw).filter { it.isDigit() }
            if (clean.length != 6) return ""
            val yy = clean.substring(0, 2).toIntOrNull() ?: return ""
            val mm = clean.substring(2, 4).toIntOrNull()?.coerceIn(1, 12) ?: return ""
            val dd = clean.substring(4, 6).toIntOrNull()?.coerceIn(1, 31) ?: return ""
            val currentTwoDigit = LocalDate.now().year % 100
            val century = if (isBirthDate) {
                if (yy > currentTwoDigit) 1900 else 2000
            } else {
                if (yy >= 70) 1900 else 2000
            }
            val year = century + yy
            return "%04d-%02d-%02d".format(year, mm, dd)
        }

        fun calculateAge(birthDateIso: String): Int {
            return runCatching {
                val dob = LocalDate.parse(birthDateIso, DateTimeFormatter.ISO_LOCAL_DATE)
                Period.between(dob, LocalDate.now()).years.coerceIn(0, 130)
            }.getOrDefault(0)
        }

        fun evaluatePassportStatus(expiryIso: String): String {
            if (expiryIso.isBlank()) return "صالح"
            return runCatching {
                val exp = LocalDate.parse(expiryIso, DateTimeFormatter.ISO_LOCAL_DATE)
                val today = LocalDate.now()
                when {
                    exp.isBefore(today) -> "منتهي"
                    exp.isBefore(today.plusMonths(6)) -> "قريب الانتهاء"
                    else -> "صالح"
                }
            }.getOrDefault("صالح")
        }

        fun validateTravelerDocument(mrz: MrzParseResult): TravelerDocumentValidation {
            val issues = mutableListOf<String>()
            if (mrz.passportNumber.isBlank()) {
                issues.add("رقم الجواز مفقود من القراءة الآلية")
            }
            if (mrz.format.startsWith("MRZ") && !mrz.passportCheckValid) {
                issues.add("عدم تطابق خانة التحقق الرقمي (Check Digit) لرقم الجواز")
            }
            if (mrz.passportStatus == "منتهي") {
                issues.add("الجواز منتهي الصلاحية (${mrz.expiryDate})")
            } else if (mrz.passportStatus == "قريب الانتهاء") {
                issues.add("صلاحية الجواز أقل من 6 أشهر (${mrz.expiryDate})")
            }
            val notExpired = mrz.passportStatus != "منتهي"
            val checkOk = !mrz.format.startsWith("MRZ") || mrz.passportCheckValid
            return TravelerDocumentValidation(
                isPassportValid = mrz.passportNumber.isNotBlank() && notExpired && checkOk,
                isCheckDigitValid = checkOk,
                isNotExpired = notExpired,
                issues = issues
            )
        }

        fun parseMrz(rawText: String): MrzParseResult? {
            val normalized = normalizeDigits(rawText).uppercase()
            val candidateLines = normalized.lines()
                .map { it.replace(" ", "").replace("«", "<").replace(Regex("[^A-Z0-9<]"), "") }
                .filter { it.length >= 28 && (it.contains("<") || it.startsWith("P")) }

            // Look for TD3 (2 lines of 44 chars)
            for (i in 0 until candidateLines.size - 1) {
                val l1Raw = candidateLines[i]
                val l2Raw = candidateLines[i + 1]
                if (l1Raw.length >= 40 && l2Raw.length >= 40 && (l1Raw.startsWith("P") || l2Raw.count { it == '<' } >= 2)) {
                    val l1 = l1Raw.padEnd(44, '<').take(44)
                    val l2 = l2Raw.padEnd(44, '<').take(44)

                    val issuingCode = l1.substring(2, 5).replace("<", "")
                    val nameBlock = l1.substring(5).split("<<", limit = 2)
                    val surname = nameBlock.getOrNull(0)?.replace("<", " ")?.trim().orEmpty()
                    val givenNames = nameBlock.getOrNull(1)?.replace("<", " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                    val fullLatin = listOf(givenNames, surname).filter { it.isNotBlank() }.joinToString(" ")

                    val passField = l2.substring(0, 9)
                    val passCheck = l2[9]
                    val passNum = passField.replace("<", "").trim()
                    val passValid = verifyCheckDigit(passField, passCheck)

                    val natCode = l2.substring(10, 13).replace("<", "").ifBlank { issuingCode }
                    val birthRaw = l2.substring(13, 19)
                    val birthCheck = l2[19]
                    val birthValid = verifyCheckDigit(cleanMrzNumberChunk(birthRaw), birthCheck)
                    val birthIso = parseYyMmDd(birthRaw, isBirthDate = true)

                    val sexChar = l2[20]
                    val genderAr = when (sexChar) {
                        'F' -> "أنثى"
                        'M' -> "ذكر"
                        else -> "ذكر"
                    }

                    val expRaw = l2.substring(21, 27)
                    val expCheck = l2[27]
                    val expValid = verifyCheckDigit(cleanMrzNumberChunk(expRaw), expCheck)
                    val expIso = parseYyMmDd(expRaw, isBirthDate = false)

                    val optField = l2.substring(28, 42)
                    val optCheck = l2[42]
                    val compositeField = l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43)
                    val compositeValid = verifyCheckDigit(compositeField, l2[43]) || verifyCheckDigit(optField, optCheck)

                    return MrzParseResult(
                        format = "MRZ-TD3",
                        passportNumber = passNum,
                        passportCheckValid = passValid,
                        surname = surname,
                        givenNames = givenNames,
                        fullLatinName = fullLatin,
                        issuingCountryCode = natCode,
                        nationalityArabic = SovereignConstants.ICAO_COUNTRY_TO_ARABIC[natCode] ?: natCode.ifBlank { "اليمن" },
                        gender = genderAr,
                        birthDate = birthIso,
                        birthCheckValid = birthValid,
                        age = calculateAge(birthIso),
                        expiryDate = expIso,
                        expiryCheckValid = expValid,
                        passportStatus = evaluatePassportStatus(expIso),
                        compositeCheckValid = compositeValid,
                        rawMrzLines = listOf(l1, l2),
                        rawOcrText = rawText
                    )
                }
            }

            // Fallback TD1 (3 lines of 30 chars for National IDs / Travel Docs)
            for (i in 0 until candidateLines.size - 2) {
                val l1Raw = candidateLines[i]
                val l2Raw = candidateLines[i + 1]
                val l3Raw = candidateLines[i + 2]
                if (l1Raw.length in 28..32 && l2Raw.length in 28..32 && l3Raw.length in 28..32) {
                    val l1 = l1Raw.padEnd(30, '<').take(30)
                    val l2 = l2Raw.padEnd(30, '<').take(30)
                    val l3 = l3Raw.padEnd(30, '<').take(30)

                    val docField = l1.substring(5, 14)
                    val docCheck = l1[14]
                    val docNum = docField.replace("<", "").trim()
                    val birthRaw = l2.substring(0, 6)
                    val birthIso = parseYyMmDd(birthRaw, isBirthDate = true)
                    val expRaw = l2.substring(8, 14)
                    val expIso = parseYyMmDd(expRaw, isBirthDate = false)
                    val natCode = l2.substring(15, 18).replace("<", "")
                    val nameBlock = l3.split("<<", limit = 2)
                    val surname = nameBlock.getOrNull(0)?.replace("<", " ")?.trim().orEmpty()
                    val givenNames = nameBlock.getOrNull(1)?.replace("<", " ")?.trim().orEmpty()

                    return MrzParseResult(
                        format = "MRZ-TD1",
                        passportNumber = docNum,
                        passportCheckValid = verifyCheckDigit(docField, docCheck),
                        surname = surname,
                        givenNames = givenNames,
                        fullLatinName = listOf(givenNames, surname).filter { it.isNotBlank() }.joinToString(" "),
                        issuingCountryCode = natCode,
                        nationalityArabic = SovereignConstants.ICAO_COUNTRY_TO_ARABIC[natCode] ?: "اليمن",
                        gender = if (l2[7] == 'F') "أنثى" else "ذكر",
                        birthDate = birthIso,
                        birthCheckValid = verifyCheckDigit(cleanMrzNumberChunk(birthRaw), l2[6]),
                        age = calculateAge(birthIso),
                        expiryDate = expIso,
                        expiryCheckValid = verifyCheckDigit(cleanMrzNumberChunk(expRaw), l2[14]),
                        passportStatus = evaluatePassportStatus(expIso),
                        compositeCheckValid = false,
                        rawMrzLines = listOf(l1, l2, l3),
                        rawOcrText = rawText
                    )
                }
            }
            return null
        }

        fun extractPassportFieldsFromText(rawText: String): MrzParseResult {
            parseMrz(rawText)?.let { return it }

            val norm = normalizeDigits(rawText)
            val passportRegex = Regex("\\b([A-Z]{0,2}[0-9]{7,9})\\b")
            val dateRegex = Regex("\\b(19\\d{2}|20\\d{2})[-/](0?[1-9]|1[0-2])[-/](0?[1-9]|[12]\\d|3[01])\\b")
            val dmyRegex = Regex("\\b(0?[1-9]|[12]\\d|3[01])[-/](0?[1-9]|1[0-2])[-/](19\\d{2}|20\\d{2})\\b")

            val foundPassport = passportRegex.find(norm.uppercase())?.value.orEmpty()
            val isoDates = dateRegex.findAll(norm).map {
                val y = it.groupValues[1]
                val m = it.groupValues[2].padStart(2, '0')
                val d = it.groupValues[3].padStart(2, '0')
                "$y-$m-$d"
            }.toMutableList()

            dmyRegex.findAll(norm).forEach {
                val d = it.groupValues[1].padStart(2, '0')
                val m = it.groupValues[2].padStart(2, '0')
                val y = it.groupValues[3]
                isoDates.add("$y-$m-$d")
            }

            val sortedDates = isoDates.distinct().sorted()
            val dob = sortedDates.firstOrNull { it < "2018-01-01" }.orEmpty()
            val exp = sortedDates.lastOrNull { it >= "2020-01-01" }.orEmpty()
            val latinName = norm.lines()
                .firstOrNull { line ->
                    val t = line.trim()
                    t.length in 6..50 && t.matches(Regex("[A-Za-z\\s\\-']+")) &&
                        !t.contains("PASSPORT", ignoreCase = true) &&
                        !t.contains("REPUBLIC", ignoreCase = true)
                }?.trim().orEmpty()

            return MrzParseResult(
                format = "OCR-TEXT",
                passportNumber = foundPassport,
                fullLatinName = latinName,
                birthDate = dob,
                age = calculateAge(dob),
                expiryDate = exp,
                passportStatus = evaluatePassportStatus(exp),
                rawOcrText = rawText
            )
        }

        suspend fun recognizeBitmap(bitmap: Bitmap): String {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            return try {
                val input = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(input).await().text
            } finally {
                runCatching { recognizer.close() }
            }
        }
    }
}
