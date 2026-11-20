package com.example.yemenport.domain.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.yemenport.data.local.TravelerEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SovereignExportEngine {

    private fun ensureExportDir(context: Context): File {
        return File(context.cacheDir, "exports").apply { mkdirs() }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 360): Bitmap? {
        return runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }

    fun exportTravelersCsv(context: Context, travelers: List<TravelerEntity>): File {
        val dir = ensureExportDir(context)
        val file = File(dir, "yemen_borders_travelers_${System.currentTimeMillis()}.csv")
        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM for Arabic Excel
            val sb = StringBuilder()
            sb.appendLine("الاسم الكامل,رقم الجواز,الجنسية,المنفذ,الوكالة,التأشيرة,تاريخ الانتهاء,السماح بالسفر,نتيجة المباينة,الحالة")
            for (t in travelers) {
                fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
                sb.appendLine(
                    listOf(
                        esc(t.fullName),
                        esc(t.idNumber),
                        esc(t.country),
                        esc(t.portName),
                        esc(t.agency),
                        esc(t.visaType),
                        esc(t.expiryDate),
                        esc(t.travelAllowed),
                        esc(t.vettingResult),
                        esc(t.status)
                    ).joinToString(",")
                )
            }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun exportTravelersXlsx(context: Context, travelers: List<TravelerEntity>): File {
        val dir = ensureExportDir(context)
        val file = File(dir, "yemen_borders_travelers_${System.currentTimeMillis()}.xlsx")

        fun xmlEsc(v: String): String = v
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        val rowsXml = StringBuilder()
        val headers = listOf("الاسم الكامل", "رقم الجواز", "الجنسية", "المنفذ", "الوكالة", "التأشيرة", "الانتهاء", "السماح", "المباينة")
        fun appendRow(idx: Int, cells: List<String>) {
            rowsXml.append("<row r=\"$idx\">")
            cells.forEachIndexed { colIdx, text ->
                val colChar = ('A'.code + colIdx).toChar()
                rowsXml.append("<c r=\"$colChar$idx\" t=\"inlineStr\"><is><t>${xmlEsc(text)}</t></is></c>")
            }
            rowsXml.append("</row>")
        }
        appendRow(1, headers)
        travelers.forEachIndexed { i, t ->
            appendRow(
                i + 2,
                listOf(t.fullName, t.idNumber, t.country, t.portName, t.agency, t.visaType, t.expiryDate, t.travelAllowed, t.vettingResult)
            )
        }

        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rowsXml</sheetData></worksheet>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Travelers" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            fun add(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            add("[Content_Types].xml", contentTypes)
            add("_rels/.rels", rels)
            add("xl/workbook.xml", workbook)
            add("xl/_rels/workbook.xml.rels", workbookRels)
            add("xl/worksheets/sheet1.xml", sheetXml)
        }
        return file
    }

    fun exportSummaryPdf(
        context: Context,
        title: String,
        lines: List<String>
    ): File {
        val dir = ensureExportDir(context)
        val file = File(dir, "yemen_borders_report_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 58, 95)
            textSize = 16f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(217, 119, 6)
            strokeWidth = 2f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 55f

        canvas.drawText("الجمهورية اليمنية — نظام المنافذ والجوازات الذكي", pageWidth - 36f, y, headerPaint)
        y += 24f
        canvas.drawText(title, pageWidth - 36f, y, bodyPaint)
        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 26f

        for (line in lines) {
            if (y > pageHeight - 50f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 50f
            }
            canvas.drawText(line.take(90), pageWidth - 36f, y, bodyPaint)
            y += 20f
        }
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة / حفظ التقرير الرسمي"))
    }
}
