package com.example.yemenport.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yemenport.data.local.TravelClearanceStatus
import com.example.yemenport.data.local.TravelEntity
import com.example.yemenport.data.local.TravelerEntity
import com.example.yemenport.domain.export.SovereignExportEngine
import com.example.yemenport.ui.OfficerSession
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TravelersScreen(
    session: OfficerSession,
    travelers: List<TravelerEntity>,
    travels: List<TravelEntity> = emptyList(),
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
    onExport: (String) -> Unit,
    onRecordCrossing: (traveler: TravelerEntity, direction: String, destination: String) -> Unit = { _, _, _ -> },
    onNavigateToScanner: () -> Unit = {}
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("الكل") }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Travelers, 1 = Travel Crossings (TravelEntity)
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedQrTraveler by remember { mutableStateOf<TravelerEntity?>(null) }
    var selectedDetailTraveler by remember { mutableStateOf<TravelerEntity?>(null) }

    val filters = listOf("الكل", "مسموح", "ممنوع / مرتجع", "قريب الانتهاء", "منتهي")
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }

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

    val filteredTravels = remember(search, filterMode, travels) {
        travels.filter { tr ->
            val q = search.trim()
            val matchesQuery = q.isEmpty() ||
                tr.travelerName.contains(q, true) ||
                tr.passportNumber.contains(q, true) ||
                tr.portName.contains(q, true) ||
                tr.destination.contains(q, true) ||
                tr.officerName.contains(q, true)
            val matchesFilter = when (filterMode) {
                "مسموح" -> tr.clearanceStatus == TravelClearanceStatus.ALLOWED
                "ممنوع / مرتجع" -> tr.clearanceStatus == TravelClearanceStatus.BLOCKED || tr.clearanceStatus == TravelClearanceStatus.RETURNED
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                label = { Text("سجلات المسافرين (${filtered.size})") },
                modifier = Modifier.testTag("tab_travelers_list")
            )
            FilterChip(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                label = { Text("حركات العبور (${filteredTravels.size})") },
                modifier = Modifier.testTag("tab_travel_crossings")
            )
        }

        if (activeTab == 1) {
            if (filteredTravels.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlightTakeoff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "لا توجد حركات عبور مسجلة مطابقة حتى الآن.\nاضغط على أي مسافر لتسجيل حركة دخول أو مغادرة.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (travelers.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { activeTab = 0 }) {
                            Text("عرض سجل المسافرين")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTravels, key = { it.id }) { tr ->
                        val statusColor = when (tr.clearanceStatus) {
                            TravelClearanceStatus.ALLOWED -> SovereignEmerald
                            TravelClearanceStatus.BLOCKED -> SovereignCrimson
                            TravelClearanceStatus.RETURNED,
                            TravelClearanceStatus.PENDING_REVIEW -> SovereignGold
                        }
                        ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("travel_crossing_card_${tr.id}")) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tr.travelerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${tr.direction} • جواز: ${tr.passportNumber.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Surface(
                                        color = statusColor.copy(alpha = 0.14f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            tr.clearanceStatus.arabicLabel,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    "المنفذ: ${tr.portName}${if (tr.destination.isNotBlank()) " → ${tr.destination}" else ""}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "التاريخ: ${timeFormatter.format(tr.crossedAt)} • الضابط: ${tr.officerName.ifBlank { "المناوب" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tr.flags.isNotEmpty()) {
                                    Text(
                                        "علامات: ${tr.flags.joinToString(" • ")}",
                                        color = SovereignCrimson,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "لا توجد سجلات مسافرين مطابقة. ابدأ بمسح الجواز بالكاميرا أو الإضافة اليدوية.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onNavigateToScanner,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("empty_scan_passport_button")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("مسح جواز الآن")
                    }
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("empty_manual_add_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تسجيل يدوي")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { t ->
                    val blocked = t.travelAllowed == "لا"
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDetailTraveler = t }
                            .testTag("traveler_card_${t.id}")
                    ) {
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

    selectedDetailTraveler?.let { t ->
        var crossingDestination by remember(t.id) { mutableStateOf(t.destination) }
        val travelerCrossings = remember(t.id, travels) {
            travels.filter {
                it.travelerId == t.id || (t.idNumber.isNotBlank() && it.passportNumber == t.idNumber)
            }
        }
        AlertDialog(
            onDismissRequest = { selectedDetailTraveler = null },
            title = { Text(t.fullName, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (t.passportName.isNotBlank()) {
                        Text("الاسم اللاتيني: ${t.passportName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("رقم الجواز: ${t.idNumber.ifBlank { "-" }} (${t.passportStatus})")
                    Text("الجنسية: ${t.country} • الجنس: ${t.gender} • الميلاد: ${t.birthDate.ifBlank { "-" }}")
                    Text("المنفذ: ${t.portName} • التأشيرة: ${t.visaType.ifBlank { "-" }}")
                    Text("الوكالة: ${t.agency.ifBlank { "-" }} • الوجهة المسجلة: ${t.destination.ifBlank { "-" }}")
                    Text(
                        "الحالة الأمنية: ${t.vettingResult.ifBlank { if (t.travelAllowed == "لا") "ممنوع" else "سليم" }}",
                        color = if (t.travelAllowed == "لا") SovereignCrimson else SovereignEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    if (t.additionalInfo.isNotBlank()) {
                        Text("ملاحظات: ${t.additionalInfo}", style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider()
                    Text("تسجيل حركة عبور جديدة عبر ${session.assignedPort}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = crossingDestination,
                        onValueChange = { crossingDestination = it },
                        label = { Text("الوجهة / بلد القدوم") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onRecordCrossing(t, "دخول", crossingDestination)
                                selectedDetailTraveler = null
                            },
                            modifier = Modifier.weight(1f).testTag("record_entry_crossing_button")
                        ) {
                            Text("تسجيل دخول")
                        }
                        FilledTonalButton(
                            onClick = {
                                onRecordCrossing(t, "مغادرة", crossingDestination)
                                selectedDetailTraveler = null
                            },
                            modifier = Modifier.weight(1f).testTag("record_exit_crossing_button")
                        ) {
                            Text("تسجيل مغادرة")
                        }
                    }

                    if (travelerCrossings.isNotEmpty()) {
                        HorizontalDivider()
                        Text("سجل حركات العبور (${travelerCrossings.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        travelerCrossings.take(5).forEach { cr ->
                            Text(
                                "• ${cr.direction} (${cr.clearanceStatus.arabicLabel}) — ${cr.portName} • ${timeFormatter.format(cr.crossedAt)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (t.mergeRecords.isNotEmpty() || t.mergeHistory.isNotBlank()) {
                        HorizontalDivider()
                        Text("الخط الزمني للدمج الذكي:", fontWeight = FontWeight.Bold, color = SovereignGold, style = MaterialTheme.typography.labelLarge)
                        if (t.mergeRecords.isNotEmpty()) {
                            t.mergeRecords.forEach { rec ->
                                Text(
                                    "• ${rec.timestampIso.take(16)} (${rec.officerName}): ${rec.changedFields.joinToString("، ")}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Text(t.mergeHistory, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDetailTraveler = null }) { Text("إغلاق") }
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
