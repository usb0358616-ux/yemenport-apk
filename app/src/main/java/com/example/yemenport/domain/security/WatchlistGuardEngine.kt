package com.example.yemenport.domain.security

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.yemenport.data.local.BorderDao
import com.example.yemenport.data.local.PassportSeizureEntity
import com.example.yemenport.data.local.SecurityReportEntity
import com.example.yemenport.data.local.TravelClearanceStatus
import com.example.yemenport.data.local.TravelEntity
import com.example.yemenport.data.local.TravelerEntity
import com.example.yemenport.domain.mrz.MrzParseResult
import com.example.yemenport.domain.mrz.PassportOcrAnalyzer
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min

enum class WatchlistMatchSource(val arabicLabel: String) {
    TRAVEL_BAN("منع سفر مسجل"),
    ACTIVE_WARRANT("أمر قبض / تعميم نشط"),
    SECURITY_REPORT("بلاغ وتعميم أمني"),
    PASSPORT_SEIZURE("محضر جواز مضبوط / مزور"),
    PREVIOUS_RETURN("حالة إرتجاع سابقة"),
    DOCUMENT_ANOMALY("خلل / انتهاء صلاحية الوثيقة")
}

data class WatchlistMatch(
    val source: WatchlistMatchSource,
    val recordId: String,
    val matchedName: String,
    val matchedPassport: String,
    val reason: String,
    val confidencePercent: Int,
    val matchType: String
)

data class WatchlistCheckSummary(
    val queryName: String,
    val queryPassport: String,
    val hits: List<WatchlistMatch>
) {
    val isCritical: Boolean get() = hits.isNotEmpty()
    val hasActiveWarrantOrBan: Boolean
        get() = hits.any {
            it.source == WatchlistMatchSource.TRAVEL_BAN ||
                it.source == WatchlistMatchSource.ACTIVE_WARRANT ||
                it.source == WatchlistMatchSource.PASSPORT_SEIZURE
        }
}

/**
 * Security screening service comparing extracted OCR/MRZ traveler data against local Room records
 * for prohibited travel statuses, active warrants, security circulars, and seized/expired passports.
 */
object WatchlistGuardEngine {

    fun normalizeArabic(text: String): String {
        return text.trim()
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Strip harakat
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    fun normalizePassport(passport: String): String {
        return passport.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]"), "")
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        val maxLen = max(a.length, b.length)
        return 1.0 - (dp[b.length].toDouble() / maxLen)
    }

    private fun nameMatchScore(queryRaw: String, targetRaw: String): Int {
        val q = normalizeArabic(queryRaw)
        val t = normalizeArabic(targetRaw)
        if (q.length < 3 || t.length < 3) return 0
        if (q == t) return 100
        if (q.contains(t) || t.contains(q)) return 92

        val qTokens = q.split(" ").filter { it.length >= 2 }.toSet()
        val tTokens = t.split(" ").filter { it.length >= 2 }.toSet()
        if (qTokens.isNotEmpty() && tTokens.isNotEmpty()) {
            val overlap = qTokens.intersect(tTokens).size
            val ratio = overlap.toDouble() / min(qTokens.size, tTokens.size)
            if (overlap >= 2 && ratio >= 0.75) {
                return (ratio * 90).toInt()
            }
        }
        val sim = (levenshteinSimilarity(q, t) * 100).toInt()
        return if (sim >= 82) sim else 0
    }

    private fun isActiveWarrantReport(report: SecurityReportEntity): Boolean {
        val marker = "${report.reportType} ${report.severity} ${report.title}"
        return marker.contains("قبض") ||
            marker.contains("توقيف") ||
            marker.contains("مذكرة") ||
            marker.contains("مطلوب") ||
            report.severity == "حرج" ||
            report.severity == "عالي"
    }

    suspend fun verifyExtractedMrz(
        mrz: MrzParseResult,
        dao: BorderDao
    ): WatchlistCheckSummary {
        val travelers = dao.getAllTravelers()
        val reports = dao.getAllReports()
        val seizures = dao.getAllSeizures()
        val passportCrossings = if (mrz.passportNumber.isNotBlank()) {
            dao.observeTravelsForPassport(mrz.passportNumber).first()
        } else {
            emptyList()
        }
        return checkExtractedMrz(
            mrz = mrz,
            travelers = travelers,
            reports = reports,
            seizures = seizures,
            travels = passportCrossings
        )
    }

    suspend fun verifyAgainstDatabase(
        queryName: String,
        queryPassport: String,
        queryLatinName: String = "",
        dao: BorderDao
    ): WatchlistCheckSummary {
        val travelers = dao.getAllTravelers()
        val reports = dao.getAllReports()
        val seizures = dao.getAllSeizures()
        val travels = if (queryPassport.isNotBlank()) {
            dao.observeTravelsForPassport(queryPassport).first()
        } else {
            emptyList()
        }
        return checkWatchlist(
            queryName = queryName,
            queryPassport = queryPassport,
            queryLatinName = queryLatinName,
            travelers = travelers,
            reports = reports,
            seizures = seizures,
            travels = travels
        )
    }

    fun checkExtractedMrz(
        mrz: MrzParseResult,
        travelers: List<TravelerEntity>,
        reports: List<SecurityReportEntity>,
        seizures: List<PassportSeizureEntity>,
        travels: List<TravelEntity> = emptyList()
    ): WatchlistCheckSummary {
        val base = checkWatchlist(
            queryName = mrz.fullLatinName,
            queryPassport = mrz.passportNumber,
            queryLatinName = mrz.fullLatinName,
            travelers = travelers,
            reports = reports,
            seizures = seizures,
            travels = travels
        )
        val validation = PassportOcrAnalyzer.validateTravelerDocument(mrz)
        val docHits = mutableListOf<WatchlistMatch>()
        if (mrz.passportStatus == "منتهي") {
            docHits.add(
                WatchlistMatch(
                    source = WatchlistMatchSource.DOCUMENT_ANOMALY,
                    recordId = "doc_exp_${mrz.passportNumber}",
                    matchedName = mrz.fullLatinName,
                    matchedPassport = mrz.passportNumber,
                    reason = "الجواز منتهي الصلاحية (${mrz.expiryDate})",
                    confidencePercent = 100,
                    matchType = "فحص صلاحية الوثيقة"
                )
            )
        } else if (!validation.isCheckDigitValid && mrz.format.startsWith("MRZ")) {
            docHits.add(
                WatchlistMatch(
                    source = WatchlistMatchSource.DOCUMENT_ANOMALY,
                    recordId = "doc_chk_${mrz.passportNumber}",
                    matchedName = mrz.fullLatinName,
                    matchedPassport = mrz.passportNumber,
                    reason = "عدم تطابق خانات التحقق الرقمية في سطر MRZ — يلزم فحص التزوير",
                    confidencePercent = 95,
                    matchType = "فحص Check Digit"
                )
            )
        }
        if (docHits.isEmpty()) return base
        return base.copy(hits = (base.hits + docHits).sortedByDescending { it.confidencePercent })
    }

    fun checkWatchlist(
        queryName: String,
        queryPassport: String,
        queryLatinName: String = "",
        travelers: List<TravelerEntity>,
        reports: List<SecurityReportEntity>,
        seizures: List<PassportSeizureEntity>,
        travels: List<TravelEntity> = emptyList()
    ): WatchlistCheckSummary {
        val hits = mutableListOf<WatchlistMatch>()
        val normPass = normalizePassport(queryPassport)

        // 1. Prohibited Travel Status & Previous Returns in TravelerEntity
        for (t in travelers) {
            val isBanned = t.travelAllowed == "لا" || t.securityFlags.contains("ممنوع_من_السفر")
            val hasReturn = t.returnReason.isNotBlank() || t.status.contains("مرتجع")
            if (!isBanned && !hasReturn) continue

            val tPass = normalizePassport(t.idNumber)
            val passMatch = normPass.isNotEmpty() && tPass == normPass
            val arScore = nameMatchScore(queryName, t.fullName)
            val latScore = if (queryLatinName.isNotBlank() && t.passportName.isNotBlank()) {
                nameMatchScore(queryLatinName, t.passportName)
            } else 0
            val bestNameScore = max(arScore, latScore)

            if (passMatch || bestNameScore >= 82) {
                hits.add(
                    WatchlistMatch(
                        source = if (isBanned) WatchlistMatchSource.TRAVEL_BAN else WatchlistMatchSource.PREVIOUS_RETURN,
                        recordId = t.id,
                        matchedName = t.fullName.ifBlank { t.passportName },
                        matchedPassport = t.idNumber,
                        reason = t.returnReason.ifBlank { t.vettingResult.ifBlank { "ممنوع من السفر" } },
                        confidencePercent = if (passMatch) 100 else bestNameScore,
                        matchType = if (passMatch) "تطابق رقم الجواز" else "تشابه اسمي ($bestNameScore%)"
                    )
                )
            }
        }

        // 2. Prior Prohibited / Returned Crossings in TravelEntity
        for (tr in travels) {
            val blocked = tr.clearanceStatus == TravelClearanceStatus.BLOCKED ||
                tr.clearanceStatus == TravelClearanceStatus.RETURNED ||
                tr.travelAllowed == "لا"
            if (!blocked) continue
            val trPass = normalizePassport(tr.passportNumber)
            val passMatch = normPass.isNotEmpty() && trPass == normPass
            val nameScore = max(
                nameMatchScore(queryName, tr.travelerName),
                nameMatchScore(queryLatinName, tr.travelerName)
            )
            if (passMatch || nameScore >= 85) {
                hits.add(
                    WatchlistMatch(
                        source = if (tr.clearanceStatus == TravelClearanceStatus.RETURNED) {
                            WatchlistMatchSource.PREVIOUS_RETURN
                        } else {
                            WatchlistMatchSource.TRAVEL_BAN
                        },
                        recordId = tr.id,
                        matchedName = tr.travelerName,
                        matchedPassport = tr.passportNumber,
                        reason = "سجل عبور مقيد (${tr.clearanceStatus.arabicLabel}) في ${tr.portName}: ${tr.vettingStatus}",
                        confidencePercent = if (passMatch) 100 else nameScore,
                        matchType = if (passMatch) "تطابق سجل العبور بالجواز" else "تشابه سجل العبور ($nameScore%)"
                    )
                )
            }
        }

        // 3. Active Warrants & Security Reports / Circulars
        for (r in reports) {
            if (r.status == "مرفوض") continue
            val rPass = normalizePassport(r.targetPassportNumber)
            val passMatch = normPass.isNotEmpty() && rPass == normPass
            val nameScore = max(
                nameMatchScore(queryName, r.targetName),
                nameMatchScore(queryLatinName, r.targetName)
            )
            if (passMatch || nameScore >= 80) {
                val source = if (isActiveWarrantReport(r)) {
                    WatchlistMatchSource.ACTIVE_WARRANT
                } else {
                    WatchlistMatchSource.SECURITY_REPORT
                }
                hits.add(
                    WatchlistMatch(
                        source = source,
                        recordId = r.id,
                        matchedName = r.targetName.ifBlank { r.title },
                        matchedPassport = r.targetPassportNumber,
                        reason = "${r.title} (${r.status}): ${r.description}".take(160),
                        confidencePercent = if (passMatch) 100 else nameScore,
                        matchType = if (passMatch) "تطابق تعميم/مذكرة بالجواز" else "تطابق اسم مع بلاغ ($nameScore%)"
                    )
                )
            }
        }

        // 4. Passport Seizures / Forged Passports
        for (s in seizures) {
            val sPass = normalizePassport(s.passportNumber)
            val passMatch = normPass.isNotEmpty() && sPass == normPass
            val nameScore = max(
                nameMatchScore(queryName, s.travelerName),
                nameMatchScore(queryLatinName, s.travelerName)
            )
            if (passMatch || nameScore >= 85) {
                hits.add(
                    WatchlistMatch(
                        source = WatchlistMatchSource.PASSPORT_SEIZURE,
                        recordId = s.id,
                        matchedName = s.travelerName,
                        matchedPassport = s.passportNumber,
                        reason = "محضر ضبط (${s.status}): ${s.reason}",
                        confidencePercent = if (passMatch) 100 else nameScore,
                        matchType = if (passMatch) "جواز مضبوط" else "تشابه مع محضر ضبط ($nameScore%)"
                    )
                )
            }
        }

        return WatchlistCheckSummary(
            queryName = queryName.ifBlank { queryLatinName },
            queryPassport = queryPassport,
            hits = hits.distinctBy { "${it.source}:${it.recordId}" }.sortedByDescending { it.confidencePercent }
        )
    }

    fun triggerCriticalFeedback(context: Context) {
        runCatching {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 100, 260), -1))
        }
    }
}
