package com.example.yemenport.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yemenport.data.local.TravelerEntity
import com.example.yemenport.domain.export.SovereignExportEngine
import com.example.yemenport.ui.OfficerSession
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold

@Composable
fun TravelersScreen(
    session: OfficerSession,
    travelers: List<TravelerEntity>,
    onAddTraveler: (
        fullName: String,
        passportName: String,
        idNumber: String,
        country: String,
        gender: String,
        birthDate: String,
        expiryDate: String,
        portName: String,
        agency: String,
        visaType: String,
        destination: String,
        travelAllowed: String,
        returnReason: String,
        notes: String
    ) -> Unit,
    onDeleteTraveler: (String) -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("الكل") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedQrTraveler by remember { mutableStateOf<TravelerEntity?>(null) }

    val filters = listOf("الكل", "مسموح", "ممنوع / مرتجع", "قريب الانتهاء", "منتهي")

    val filtered = remember(search, filterMode, travelers) {
        travelers.filter { t ->
            val q = search.trim()
            val matchesQuery = q.isEmpty() ||
                t.fullName.contains(q, true) ||
                t.passportName.contains(q, true) ||
                t.idNumber.contains(q, true) ||
                t.agency.contains(q, true) ||
                t.portName.contains(q, true)
            val matchesFilter = when (filterMode) {
                "مسموح" -> t.travelAllowed == "نعم" && t.returnReason.isBlank()
                "ممنوع / مرتجع" -> t.travelAllowed == "لا" || t.returnReason.isNotBlank() || t.status.contains("مرتجع")
                "قريب الانتهاء" -> t.passportStatus == "قريب الانتهاء"
                "منتهي" -> t.passportStatus == "منتهي"
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سجل المسافرين والتأشيرات (${filtered.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_traveler_button")) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("إضافة")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("بحث بالاسم، رقم الجواز، الوكالة، أو المنفذ...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("travelers_search_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { f ->
                FilterChip(
                    selected = filterMode == f,
                    onClick = { filterMode = f },
                    label = { Text(f) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onExport("CSV") }, modifier = Modifier.weight(1f).testTag("export_csv_btn")) {
                Text("CSV")
            }
            OutlinedButton(onClick = { onExport("XLSX") }, modifier = Modifier.weight(1f).testTag("export_xlsx_btn")) {
                Text("Excel")
            }
            OutlinedButton(onClick = { onExport("PDF") }, modifier = Modifier.weight(1f).testTag("export_pdf_btn")) {
                Text("PDF")
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "لا توجد سجلات مطابقة. يمكنك إضافة مسافر، المسح بالكاميرا، أو استيراد كشف.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { t ->
                    val blocked = t.travelAllowed == "لا"
                    ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("traveler_card_${t.id}")) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (t.passportName.isNotBlank() && t.passportName != t.fullName) {
                                        Text(t.passportName, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Surface(
                                    color = if (blocked) SovereignCrimson.copy(alpha = 0.14f) else SovereignEmerald.copy(alpha = 0.14f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        if (blocked) "ممنوع" else "مسموح",
                                        color = if (blocked) SovereignCrimson else SovereignEmerald,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                "جواز: ${t.idNumber.ifBlank { "-" }} (${t.passportStatus}) • الجنسية: ${t.country} • المنفذ: ${t.portName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (t.agency.isNotBlank() || t.visaType.isNotBlank()) {
                                Text("الوكالة: ${t.agency.ifBlank { "-" }} • التأشيرة: ${t.visaType.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (t.vettingResult.isNotBlank() && t.vettingResult != "سليم") {
                                Text("المباينة الأمنية: ${t.vettingResult}", color = SovereignCrimson, style = MaterialTheme.typography.labelLarge)
                            }
                            if (t.mergeHistory.isNotBlank()) {
                                Text("سجل الدمج الذكي متاح (${t.mergeHistory.lines().size} تحديث)", color = SovereignGold, style = MaterialTheme.typography.labelSmall)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { selectedQrTraveler = t }) {
                                    Icon(Icons.Default.QrCode2, contentDescription = "بطاقة QR", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("بطاقة QR")
                                }
                                if (session.role.canDeleteRecords) {
                                    TextButton(
                                        onClick = { onDeleteTraveler(t.id) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = SovereignCrimson)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("حذف")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var passport by remember { mutableStateOf("") }
        var nat by remember { mutableStateOf("اليمن") }
        var agency by remember { mutableStateOf("") }
        var visa by remember { mutableStateOf("تأشيرة عمل") }
        var expiry by remember { mutableStateOf("") }
        var allowed by remember { mutableStateOf("نعم") }
        var returnReason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("تسجيل مسافر / تأشيرة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الكامل") }, singleLine = true)
                    OutlinedTextField(value = passport, onValueChange = { passport = it }, label = { Text("رقم الجواز") }, singleLine = true)
                    OutlinedTextField(value = nat, onValueChange = { nat = it }, label = { Text("الجنسية") }, singleLine = true)
                    OutlinedTextField(value = agency, onValueChange = { agency = it }, label = { Text("الوكالة") }, singleLine = true)
                    OutlinedTextField(value = visa, onValueChange = { visa = it }, label = { Text("نوع التأشيرة") }, singleLine = true)
                    OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("تاريخ الانتهاء (YYYY-MM-DD)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = allowed == "نعم", onClick = { allowed = "نعم" }, label = { Text("مسموح") })
                        FilterChip(selected = allowed == "لا", onClick = { allowed = "لا" }, label = { Text("منع سفر") })
                    }
                    if (allowed == "لا") {
                        OutlinedTextField(value = returnReason, onValueChange = { returnReason = it }, label = { Text("سبب المنع / الإرتجاع") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTraveler(
                            name, "", passport, nat, "ذكر", "", expiry,
                            session.assignedPort, agency, visa, "", allowed, returnReason, ""
                        )
                        showAddDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") }
            }
        )
    }

    selectedQrTraveler?.let { t ->
        val qrBmp = remember(t.id) {
            SovereignExportEngine.generateQrBitmap(
                "YEM-BORDER|${t.idNumber}|${t.fullName}|${t.country}|ALLOWED:${t.travelAllowed}|PORT:${t.portName}"
            )
        }
        AlertDialog(
            onDismissRequest = { selectedQrTraveler = null },
            title = { Text("بطاقة التحقق الرقمية") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (qrBmp != null) {
                        Image(bitmap = qrBmp.asImageBitmap(), contentDescription = "رمز QR للمسافر", modifier = Modifier.size(180.dp))
                    }
                    Text(t.fullName, fontWeight = FontWeight.Bold)
                    Text("جواز: ${t.idNumber} | السماح: ${t.travelAllowed}")
                    if (t.mergeHistory.isNotBlank()) {
                        Text("سجل الدمج:\n${t.mergeHistory}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedQrTraveler = null }) { Text("إغلاق") }
            }
        )
    }
}
