package com.example.yemenport.domain.importengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.yemenport.data.local.*
import com.example.yemenport.domain.mrz.PassportOcrAnalyzer
import com.example.yemenport.domain.security.WatchlistGuardEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipInputStream

enum class ImportTargetCategory(val arabicTitle: String) {
    TRAVELERS("سجل المسافرين والتأشيرات"),
    MOFA_MEMOS("مذكرات الخارجية والمنظمات"),
    FLIGHT_MANIFEST("مانيفست رحلات الطيران الإنساني"),
    FOREIGNERS("الوافدون الأجانب والإقامات"),
    SEIZURES("محاضر ضبط الجوازات"),
    CORRESPONDENCE("المراسلات والخطابات الرسمية")
}

data class StagedFileItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val extension: String
) {
    val readableSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

data class EditableImportRow(
    val rowId: String = UUID.randomUUID().toString(),
    val sourceFile: String,
    val serialNumber: String = "",
    val fullName: String = "",
    val passportNumber: String = "",
    val nationality: String = "اليمن",
    val birthDate: String = "",
    val expiryDate: String = "",
    val agencyOrOrg: String = "",
    val visaOrResidenceType: String = "",
    val portName: String = "المركز الرئيسي - العاصمة صنعاء",
    val status: String = "معتمد",
    val returnReason: String = "",
    val flightRoute: String = "",
    val extraNotes: String = "",
    val watchlistWarning: String = "",
    val selected: Boolean = true
)

data class StagedFileAnalysis(
    val fileName: String,
    val detectedCategory: ImportTargetCategory,
    val confidencePercent: Int,
    val rowCount: Int,
    val headerPreview: List<String>,
    val extractedRawSample: String
)

data class StagedImportBatch(
    val fileAnalyses: List<StagedFileAnalysis>,
    val targetCategory: ImportTargetCategory,
    val rows: List<EditableImportRow>
)

data class CommitImportResult(
    val committedCount: Int,
    val smartMergedCount: Int,
    val watchlistHits: Int,
    val agenciesUpdated: Int,
    val targetCategory: ImportTargetCategory
)

object UnifiedDocumentParser {

    private val NOISE_PATTERNS = listOf(
        Regex("في\\s+الجدول\\s*:?", RegexOption.IGNORE_CASE),
        Regex("في\\s+الكشف\\s*:?", RegexOption.IGNORE_CASE),
        Regex("كشف\\s+أسماء\\s*:?", RegexOption.IGNORE_CASE),
        Regex("رقم\\s+الجواز\\s*:?", RegexOption.IGNORE_CASE),
        Regex("الاسم\\s*:?", RegexOption.IGNORE_CASE),
        Regex("[\\uFFFD]+") // Remove corrupted replacement chars
    )

    private val FOOTER_KEYWORDS = listOf("الإجمالي", "الاجمالي", "المجموع", "total", "grand total", "نهاية الكشف")

    fun cleanArabicText(raw: String): String {
        var out = PassportOcrAnalyzer.normalizeDigits(raw)
        for (rx in NOISE_PATTERNS) {
            out = out.replace(rx, " ")
        }
        return out.replace(Regex("\\s+"), " ").trim()
    }

    fun normalizeDateString(raw: String): String {
        val clean = PassportOcrAnalyzer.normalizeDigits(raw).trim()
        val iso = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").find(clean)
        if (iso != null) {
            return "%04d-%02d-%02d".format(
                iso.groupValues[1].toInt(),
                iso.groupValues[2].toInt().coerceIn(1, 12),
                iso.groupValues[3].toInt().coerceIn(1, 31)
            )
        }
        val dmy = Regex("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})").find(clean)
        if (dmy != null) {
            return "%04d-%02d-%02d".format(
                dmy.groupValues[3].toInt(),
                dmy.groupValues[2].toInt().coerceIn(1, 12),
                dmy.groupValues[1].toInt().coerceIn(1, 31)
            )
        }
        return clean
    }

    fun inspectUri(context: Context, uri: Uri): StagedFileItem {
        var displayName = "document_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        val ext = displayName.substringAfterLast('.', "").uppercase().ifBlank { "DOC" }
        return StagedFileItem(uri = uri, name = displayName, sizeBytes = size, extension = ext)
    }

    private data class MappedColumns(
        val serial: Int = -1,
        val fullName: Int = -1,
        val passport: Int = -1,
        val nationality: Int = -1,
        val birthDate: Int = -1,
        val expiryDate: Int = -1,
        val agency: Int = -1,
        val visaType: Int = -1,
        val port: Int = -1,
        val status: Int = -1,
        val returnReason: Int = -1,
        val route: Int = -1
    )

    private fun mapColumnsTwoPass(headers: List<String>): MappedColumns {
        val norm = headers.map { WatchlistGuardEngine.normalizeArabic(it) }

        fun find(vararg keys: String): Int {
            // Pass 1: Exact normalized match
            for (k in keys) {
                val nk = WatchlistGuardEngine.normalizeArabic(k)
                val exact = norm.indexOfFirst { it == nk }
                if (exact >= 0) return exact
            }
            // Pass 2: Fuzzy substring / keyword match
            for (k in keys) {
                val nk = WatchlistGuardEngine.normalizeArabic(k)
                val partial = norm.indexOfFirst { it.contains(nk) || nk.contains(it) && it.length >= 3 }
                if (partial >= 0) return partial
            }
            return -1
        }

        return MappedColumns(
            serial = find("م", "الرقم", "ت", "serial", "no", "#"),
            fullName = find("الاسم الكامل", "الاسم", "اسم المسافر", "اسم الراكب", "full name", "passenger name", "name"),
            passport = find("رقم الجواز", "الجواز", "رقم الوثيقه", "passport no", "passport number", "passport"),
            nationality = find("الجنسيه", "الدوله", "nationality", "country"),
            birthDate = find("تاريخ الميلاد", "الميلاد", "dob", "birth date"),
            expiryDate = find("تاريخ الانتهاء", "انتهاء الجواز", "انتهاء الاقامه", "expiry", "expiry date"),
            agency = find("الوكاله", "اسم الوكاله", "مكتب السفر", "المنظمه", "الكفيل", "agency", "organization", "sponsor"),
            visaType = find("التاشيره", "نوع التاشيره", "رقم الاقامه", "المهنه", "visa", "visa type", "residence"),
            port = find("المنفذ", "المركز", "port", "center"),
            status = find("الحاله", "حاله الجواز", "status"),
            returnReason = find("سبب الارتجاع", "سبب المنع", "ملاحظات", "return reason", "notes", "remarks"),
            route = find("خط السير", "الرحله", "الوجهه", "route", "flight", "destination")
        )
    }

    private fun classifyCategory(
        fileName: String,
        headers: List<String>,
        rawText: String,
        learned: List<LearnedPatternEntity>
    ): ImportTargetCategory {
        val combined = WatchlistGuardEngine.normalizeArabic("$fileName ${headers.joinToString(" ")} ${rawText.take(600)}")

        for (rule in learned.sortedByDescending { it.frequency }) {
            val kws = rule.keywordsCsv.split(",").map { WatchlistGuardEngine.normalizeArabic(it) }.filter { it.isNotBlank() }
            if (kws.isNotEmpty() && kws.any { combined.contains(it) }) {
                ImportTargetCategory.entries.firstOrNull { it.arabicTitle == rule.targetCategory }?.let { return it }
            }
        }

        return when {
            combined.contains("manifest") || combined.contains("مانيفست") || combined.contains("msf") ||
                combined.contains("osesgy") || combined.contains("unhas") || combined.contains("amm-ade") ->
                ImportTargetCategory.FLIGHT_MANIFEST
            combined.contains("mofa") || combined.contains("الخارجيه") || combined.contains("مذكره") ||
                combined.contains("اجياد") || combined.contains("القوافل") || combined.contains("منظمه") ->
                ImportTargetCategory.MOFA_MEMOS
            combined.contains("اقامه") || combined.contains("كفيل") || combined.contains("الوافدين") ||
                combined.contains("residence") || combined.contains("employer") ->
                ImportTargetCategory.FOREIGNERS
            combined.contains("ضبط") || combined.contains("مضبوط") || combined.contains("تزوير") ||
                combined.contains("seizure") ->
                ImportTargetCategory.SEIZURES
            headers.isEmpty() && rawText.lines().count { it.isNotBlank() } <= 15 && !combined.contains("passport") ->
                ImportTargetCategory.CORRESPONDENCE
            else -> ImportTargetCategory.TRAVELERS
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val nonEmpty = text.lines().filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) return emptyList()
        val first = nonEmpty.first().removePrefix("\uFEFF")
        val delimiter = when {
            first.count { it == '\t' } > first.count { it == ',' } -> '\t'
            first.count { it == ';' } > first.count { it == ',' } -> ';'
            else -> ','
        }
        return nonEmpty.map { line ->
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var inQuotes = false
            for (ch in line.removePrefix("\uFEFF")) {
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    ch == delimiter && !inQuotes -> {
                        out.add(cleanArabicText(cur.toString()))
                        cur.setLength(0)
                    }
                    else -> cur.append(ch)
                }
            }
            out.add(cleanArabicText(cur.toString()))
            out
        }
    }

    private fun parseXlsxRows(inputStream: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<List<String>>()
        val zipBytes = inputStream.readBytes()

        // First pass: sharedStrings.xml
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(zis, "UTF-8")
                    var inSi = false
                    var inT = false
                    val buf = StringBuilder()
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "si" -> { inSi = true; buf.setLength(0) }
                                "t" -> if (inSi) inT = true
                            }
                            XmlPullParser.TEXT -> if (inSi && inT) buf.append(parser.text ?: "")
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "t" -> inT = false
                                "si" -> { sharedStrings.add(cleanArabicText(buf.toString())); inSi = false }
                            }
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }

        // Second pass: first worksheet
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(zis, "UTF-8")
                    var currentRow = mutableListOf<String>()
                    var cellType = ""
                    var cellRef = ""
                    var inValue = false
                    var valueBuf = StringBuilder()
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "row" -> currentRow = mutableListOf()
                                "c" -> {
                                    cellType = parser.getAttributeValue(null, "t").orEmpty()
                                    cellRef = parser.getAttributeValue(null, "r").orEmpty()
                                    valueBuf.setLength(0)
                                }
                                "v", "t" -> inValue = true
                            }
                            XmlPullParser.TEXT -> if (inValue) valueBuf.append(parser.text ?: "")
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "v", "t" -> inValue = false
                                "c" -> {
                                    val colIdx = columnRefToIndex(cellRef)
                                    while (currentRow.size < colIdx) currentRow.add("")
                                    val rawVal = valueBuf.toString().trim()
                                    val decoded = if (cellType == "s") {
                                        rawVal.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                                    } else {
                                        cleanArabicText(rawVal)
                                    }
                                    currentRow.add(decoded)
                                }
                                "row" -> if (currentRow.any { it.isNotBlank() }) sheetRows.add(currentRow)
                            }
                        }
                        event = parser.next()
                    }
                    if (sheetRows.isNotEmpty()) break
                }
                entry = zis.nextEntry
            }
        }
        return sheetRows
    }

    private fun columnRefToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var idx = 0
        for (ch in letters) {
            idx = idx * 26 + (ch - 'A' + 1)
        }
        return (idx - 1).coerceAtLeast(0)
    }

    private suspend fun extractTextFromPdf(context: Context, uri: Uri): String {
        // Attempt 1: Native text layer via PDFBox-Android
        val pdfBoxText = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    PDFTextStripper().getText(doc)
                }
            }.orEmpty()
        }.getOrDefault("")

        if (pdfBoxText.replace(Regex("\\s+"), "").length >= 25) {
            return pdfBoxText
        }

        // Attempt 2: Scanned/Image/Rotated PDF -> PdfRenderer + ML Kit OCR
        val ocrText = StringBuilder()
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val maxPages = renderer.pageCount.coerceAtMost(8)
                    for (p in 0 until maxPages) {
                        renderer.openPage(p).use { page ->
                            val scale = 2
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).coerceAtMost(2400),
                                (page.height * scale).coerceAtMost(3200),
                                Bitmap.Config.ARGB_8888
                            )
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pageText = PassportOcrAnalyzer.recognizeBitmap(bmp)
                            bmp.recycle()
                            if (pageText.isNotBlank()) {
                                ocrText.append(pageText).append("\n")
                            }
                        }
                    }
                }
            }
        }
        return ocrText.toString().ifBlank { pdfBoxText }
    }

    suspend fun parseStagedFiles(
        context: Context,
        files: List<StagedFileItem>,
        defaultPort: String,
        travelers: List<TravelerEntity>,
        reports: List<SecurityReportEntity>,
        seizures: List<PassportSeizureEntity>,
        learned: List<LearnedPatternEntity>,
        onProgress: (Int, String) -> Unit
    ): StagedImportBatch = withContext(Dispatchers.Default) {
        val analyses = mutableListOf<StagedFileAnalysis>()
        val allRows = mutableListOf<EditableImportRow>()
        var dominantCategory = ImportTargetCategory.TRAVELERS

        files.forEachIndexed { idx, file ->
            onProgress(((idx * 100) / files.size.coerceAtLeast(1)), "جاري قراءة وتحليل: ${file.name}")

            var headers = emptyList<String>()
            var tableRows: List<List<String>> = emptyList()
            var rawText = ""

            when (file.extension) {
                "XLSX", "XLSM" -> {
                    tableRows = runCatching {
                        context.contentResolver.openInputStream(file.uri)?.use { parseXlsxRows(it) } ?: emptyList()
                    }.getOrDefault(emptyList())
                }
                "CSV", "TSV", "TXT", "XLS" -> {
                    rawText = runCatching {
                        context.contentResolver.openInputStream(file.uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        }.orEmpty()
                    }.getOrDefault("")
                    tableRows = parseCsvRows(rawText)
                }
                "PDF" -> {
                    rawText = extractTextFromPdf(context, file.uri)
                    tableRows = extractStructuredRowsFromFreeText(rawText)
                }
                "JPG", "JPEG", "PNG", "WEBP" -> {
                    rawText = runCatching {
                        context.contentResolver.openInputStream(file.uri)?.use { stream ->
                            val bmp = BitmapFactory.decodeStream(stream)
                            if (bmp != null) {
                                val txt = PassportOcrAnalyzer.recognizeBitmap(bmp)
                                bmp.recycle()
                                txt
                            } else ""
                        }.orEmpty()
                    }.getOrDefault("")
                    val mrz = PassportOcrAnalyzer.extractPassportFieldsFromText(rawText)
                    if (mrz.passportNumber.isNotBlank() || mrz.fullLatinName.isNotBlank()) {
                        tableRows = listOf(
                            listOf("الاسم", "رقم الجواز", "الجنسية", "تاريخ الميلاد", "تاريخ الانتهاء", "الحالة"),
                            listOf(
                                mrz.fullLatinName.ifBlank { "مسافر ممسوح ضوئياً" },
                                mrz.passportNumber,
                                mrz.nationalityArabic,
                                mrz.birthDate,
                                mrz.expiryDate,
                                mrz.passportStatus
                            )
                        )
                    } else {
                        tableRows = extractStructuredRowsFromFreeText(rawText)
                    }
                }
                else -> {
                    rawText = runCatching {
                        context.contentResolver.openInputStream(file.uri)?.use {
                            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
                        }.orEmpty()
                    }.getOrDefault("")
                    tableRows = parseCsvRows(rawText)
                }
            }

            if (tableRows.isNotEmpty()) {
                headers = tableRows.first()
            }

            val category = classifyCategory(file.name, headers, rawText, learned)
            if (idx == 0) dominantCategory = category

            val fileEditableRows = mutableListOf<EditableImportRow>()
            if (tableRows.size > 1) {
                val plan = mapColumnsTwoPass(headers)
                for (rIdx in 1 until tableRows.size) {
                    val cols = tableRows[rIdx]
                    val rowJoined = cols.joinToString(" ")
                    if (FOOTER_KEYWORDS.any { rowJoined.contains(it, ignoreCase = true) }) continue

                    fun col(index: Int): String = if (index in cols.indices) cols[index].trim() else ""

                    val fullName = col(plan.fullName).ifBlank {
                        cols.firstOrNull { c -> c.length >= 4 && !c.matches(Regex("[0-9A-Z\\-]{5,}")) }.orEmpty()
                    }
                    val passport = col(plan.passport).ifBlank {
                        cols.firstOrNull { c -> c.matches(Regex("[A-Za-z0-9]{6,12}")) && c.any { ch -> ch.isDigit() } }.orEmpty()
                    }
                    if (fullName.isBlank() && passport.isBlank()) continue

                    val guard = WatchlistGuardEngine.checkWatchlist(
                        queryName = fullName,
                        queryPassport = passport,
                        travelers = travelers,
                        reports = reports,
                        seizures = seizures
                    )

                    fileEditableRows.add(
                        EditableImportRow(
                            sourceFile = file.name,
                            serialNumber = col(plan.serial),
                            fullName = fullName.ifBlank { "سجل بدون اسم" },
                            passportNumber = passport,
                            nationality = col(plan.nationality).ifBlank { "اليمن" },
                            birthDate = normalizeDateString(col(plan.birthDate)),
                            expiryDate = normalizeDateString(col(plan.expiryDate)),
                            agencyOrOrg = col(plan.agency),
                            visaOrResidenceType = col(plan.visaType),
                            portName = col(plan.port).ifBlank { defaultPort },
                            status = col(plan.status).ifBlank { if (guard.isCritical) "مؤشر أمنياً" else "معتمد" },
                            returnReason = col(plan.returnReason),
                            flightRoute = col(plan.route),
                            watchlistWarning = guard.hits.firstOrNull()?.let { "${it.source.arabicLabel}: ${it.reason}" }.orEmpty()
                        )
                    )
                    if (rIdx % 250 == 0) yield()
                }
            } else if (rawText.isNotBlank()) {
                // Non-tabular document (Memo / Official Letter / Single Directive)
                fileEditableRows.add(
                    EditableImportRow(
                        sourceFile = file.name,
                        fullName = file.name.substringBeforeLast('.'),
                        agencyOrOrg = if (category == ImportTargetCategory.MOFA_MEMOS) "وزارة الخارجية / منظمة دولية" else "جهة واردة",
                        portName = defaultPort,
                        status = "وارد للأرشفة",
                        extraNotes = cleanArabicText(rawText).take(400)
                    )
                )
            }

            analyses.add(
                StagedFileAnalysis(
                    fileName = file.name,
                    detectedCategory = category,
                    confidencePercent = if (fileEditableRows.isNotEmpty()) 94 else 78,
                    rowCount = fileEditableRows.size,
                    headerPreview = headers.take(6),
                    extractedRawSample = rawText.take(280)
                )
            )
            allRows.addAll(fileEditableRows)
        }

        onProgress(100, "جاهز للمراجعة التفاعلية (${allRows.size} سجل)")
        StagedImportBatch(
            fileAnalyses = analyses,
            targetCategory = dominantCategory,
            rows = allRows
        )
    }

    private fun extractStructuredRowsFromFreeText(rawText: String): List<List<String>> {
        val lines = rawText.lines().map { cleanArabicText(it) }.filter { it.length >= 4 }
        if (lines.isEmpty()) return emptyList()

        val extracted = mutableListOf<List<String>>()
        val passRegex = Regex("\\b([A-Z0-9]{6,11})\\b")
        for (line in lines) {
            if (FOOTER_KEYWORDS.any { line.contains(it, ignoreCase = true) }) continue
            val m = passRegex.find(line)
            if (m != null && m.value.any { it.isDigit() }) {
                val pass = m.value
                val namePart = line.replace(pass, "").replace(Regex("^\\d+\\s*[-.)]?\\s*"), "").trim()
                if (namePart.length >= 3) {
                    extracted.add(listOf(namePart, pass))
                }
            }
        }
        return if (extracted.isNotEmpty()) {
            listOf(listOf("الاسم", "رقم الجواز")) + extracted
        } else {
            emptyList()
        }
    }

    suspend fun commitBatch(
        dao: BorderDao,
        batch: StagedImportBatch,
        officerName: String,
        officerRole: String,
        defaultPort: String
    ): CommitImportResult = withContext(Dispatchers.Default) {
        val activeRows = batch.rows.filter { it.selected }
        var committed = 0
        var merged = 0
        var watchlistHits = 0
        val existingAgencies = dao.getAllAgencies().associateBy { WatchlistGuardEngine.normalizeArabic(it.agencyName) }.toMutableMap()
        val touchedAgencies = mutableSetOf<String>()

        when (batch.targetCategory) {
            ImportTargetCategory.TRAVELERS, ImportTargetCategory.FLIGHT_MANIFEST -> {
                if (batch.targetCategory == ImportTargetCategory.FLIGHT_MANIFEST && activeRows.isNotEmpty()) {
                    dao.upsertMofaVisa(
                        MofaVisaEntity(
                            id = "mofa_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            memoNumber = "MNF-${System.currentTimeMillis().toString().takeLast(5)}",
                            memoDate = java.time.LocalDate.now().toString(),
                            organization = activeRows.firstOrNull { it.agencyOrOrg.isNotBlank() }?.agencyOrOrg ?: "رحلة إنسانية / UN / MSF",
                            requestType = "مانيفست رحلة طيران",
                            flightRoute = activeRows.firstOrNull { it.flightRoute.isNotBlank() }?.flightRoute ?: "SAH-ADE-AMM",
                            beneficiariesSummary = activeRows.take(5).joinToString("، ") { it.fullName },
                            beneficiariesCount = activeRows.size,
                            approvalStatus = "معتمد رسمياً"
                        )
                    )
                }

                for (row in activeRows) {
                    val hasAlert = row.watchlistWarning.isNotBlank()
                    if (hasAlert) watchlistHits++

                    val agName = row.agencyOrOrg.trim()
                    var agId = ""
                    if (agName.isNotEmpty()) {
                        val normAg = WatchlistGuardEngine.normalizeArabic(agName)
                        val cur = existingAgencies[normAg]
                        val isRet = row.status.contains("مرتجع") || row.returnReason.isNotBlank()
                        val updatedAg = TravelAgencyEntity(
                            id = cur?.id ?: "cmp_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            agencyName = cur?.agencyName ?: agName,
                            center = cur?.center?.ifBlank { row.portName } ?: row.portName,
                            linkedVisasCount = (cur?.linkedVisasCount ?: 0) + 1,
                            issuedCount = (cur?.issuedCount ?: 0) + if (!isRet) 1 else 0,
                            returnedCount = (cur?.returnedCount ?: 0) + if (isRet) 1 else 0
                        )
                        existingAgencies[normAg] = updatedAg
                        touchedAgencies.add(normAg)
                        agId = updatedAg.id
                    }

                    val existingTraveler = if (row.passportNumber.isNotBlank()) {
                        dao.findTravelerByPassport(row.passportNumber.trim())
                    } else null

                    if (existingTraveler != null) {
                        dao.upsertTraveler(
                            existingTraveler.copy(
                                fullName = row.fullName.ifBlank { existingTraveler.fullName },
                                agency = agName.ifBlank { existingTraveler.agency },
                                agencyId = agId.ifBlank { existingTraveler.agencyId },
                                expiryDate = row.expiryDate.ifBlank { existingTraveler.expiryDate },
                                travelAllowed = if (hasAlert) "لا" else existingTraveler.travelAllowed,
                                vettingResult = if (hasAlert) row.watchlistWarning else existingTraveler.vettingResult,
                                mergeHistory = (existingTraveler.mergeHistory + "\n[استيراد ${row.sourceFile}]").trim(),
                                synced = false,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        merged++
                    } else {
                        dao.upsertTraveler(
                            TravelerEntity(
                                id = "trv_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                                serialNumber = row.serialNumber,
                                fullName = row.fullName,
                                idNumber = row.passportNumber,
                                country = row.nationality.ifBlank { "اليمن" },
                                birthDate = row.birthDate,
                                expiryDate = row.expiryDate,
                                portName = row.portName.ifBlank { defaultPort },
                                center = row.portName.ifBlank { defaultPort },
                                agency = agName,
                                agencyId = agId,
                                visaType = row.visaOrResidenceType,
                                destination = row.flightRoute,
                                status = row.status,
                                returnReason = row.returnReason,
                                travelAllowed = if (hasAlert) "لا" else "نعم",
                                vettingResult = if (hasAlert) row.watchlistWarning else "سليم",
                                investigatingOfficer = officerName,
                                additionalInfo = row.extraNotes
                            )
                        )
                    }
                    committed++
                }
            }
            ImportTargetCategory.MOFA_MEMOS -> {
                for (row in activeRows) {
                    dao.upsertMofaVisa(
                        MofaVisaEntity(
                            id = "mofa_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            memoNumber = row.serialNumber.ifBlank { "MOFA-${System.currentTimeMillis().toString().takeLast(4)}" },
                            memoDate = java.time.LocalDate.now().toString(),
                            organization = row.agencyOrOrg.ifBlank { "وزارة الخارجية" },
                            requestType = "طلب تأشيرة دخول",
                            beneficiariesSummary = "${row.fullName} (${row.passportNumber}) ${row.extraNotes}".trim(),
                            beneficiariesCount = 1,
                            approvalStatus = "معتمد رسمياً"
                        )
                    )
                    committed++
                }
            }
            ImportTargetCategory.FOREIGNERS -> {
                for (row in activeRows) {
                    dao.upsertForeigner(
                        ForeignerResidentEntity(
                            id = "frg_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            residenceNumber = row.visaOrResidenceType.ifBlank { "RES-${committed + 100}" },
                            fullName = row.fullName,
                            passportNumber = row.passportNumber,
                            nationality = row.nationality,
                            employer = row.agencyOrOrg.ifBlank { "قطاع خاص" },
                            residenceExpiryDate = row.expiryDate
                        )
                    )
                    committed++
                }
            }
            ImportTargetCategory.SEIZURES -> {
                for (row in activeRows) {
                    dao.upsertSeizure(
                        PassportSeizureEntity(
                            id = "sz_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            recordNumber = row.serialNumber.ifBlank { "SZ-${System.currentTimeMillis().toString().takeLast(5)}" },
                            travelerName = row.fullName,
                            passportNumber = row.passportNumber,
                            nationality = row.nationality,
                            seizurePort = row.portName,
                            seizureDate = java.time.LocalDate.now().toString(),
                            reason = row.returnReason.ifBlank { row.extraNotes.ifBlank { "ضبط وارد عبر الاستيراد" } },
                            status = "جديد"
                        )
                    )
                    committed++
                }
            }
            ImportTargetCategory.CORRESPONDENCE -> {
                for (row in activeRows) {
                    dao.upsertCorrespondence(
                        CorrespondenceEntity(
                            id = "cor_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            referenceNumber = row.serialNumber.ifBlank { "DOC-${System.currentTimeMillis().toString().takeLast(5)}" },
                            direction = "وارد",
                            letterType = "مذكرة واردة",
                            subject = row.fullName.ifBlank { row.sourceFile },
                            summary = row.extraNotes,
                            senderEntity = row.agencyOrOrg.ifBlank { "الاستيراد الموحد" },
                            recipientEntity = defaultPort,
                            gregorianDate = java.time.LocalDate.now().toString()
                        )
                    )
                    committed++
                }
            }
        }

        if (touchedAgencies.isNotEmpty()) {
            dao.upsertAgenciesBatch(existingAgencies.values.toList())
        }

        batch.fileAnalyses.firstOrNull()?.let { firstAnalysis ->
            if (firstAnalysis.headerPreview.isNotEmpty()) {
                dao.upsertLearnedPattern(
                    LearnedPatternEntity(
                        id = "pat_${firstAnalysis.headerPreview.joinToString("_").hashCode()}",
                        patternType = "column_signature",
                        instructionText = "توجيه متعلم: ${firstAnalysis.fileName} -> ${batch.targetCategory.arabicTitle}",
                        keywordsCsv = firstAnalysis.headerPreview.joinToString(","),
                        targetCategory = batch.targetCategory.arabicTitle
                    )
                )
            }
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                action = "استيراد ملفات موحد",
                entityType = batch.targetCategory.name,
                details = "استيراد $committed سجل ($merged دمج ذكي، $watchlistHits تطابق مطلوبين) إلى ${batch.targetCategory.arabicTitle}",
                officerName = officerName,
                officerRole = officerRole,
                portName = defaultPort,
                timestampIso = java.time.Instant.now().toString()
            )
        )

        CommitImportResult(
            committedCount = committed,
            smartMergedCount = merged,
            watchlistHits = watchlistHits,
            agenciesUpdated = touchedAgencies.size,
            targetCategory = batch.targetCategory
        )
    }
}
