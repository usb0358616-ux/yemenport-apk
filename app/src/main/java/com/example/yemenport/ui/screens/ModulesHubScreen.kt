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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yemenport.core.SovereignConstants
import com.example.yemenport.data.local.*
import com.example.yemenport.domain.export.SovereignExportEngine
import com.example.yemenport.ui.OfficerSession
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold

enum class SovereignModuleTab(val label: String) {
    REPORTS("البلاغات والتعميمات"),
    SEIZURES("مضبوطات الجوازات"),
    MOVEMENTS("حركة الدوريات"),
    PERMITS("تصاريح التنقل QR"),
    MOFA("مذكرات الخارجية والرحلات"),
    RESIDENTS("الوافدون واللاجئون"),
    CITIZENSHIP("الجنسية و SLA"),
    AGENCIES("الوكالات والمراسلات"),
    PORTS("المنافذ (19)")
}

@Composable
fun ModulesHubScreen(
    session: OfficerSession,
    initialTab: SovereignModuleTab = SovereignModuleTab.REPORTS,
    reports: List<SecurityReportEntity>,
    seizures: List<PassportSeizureEntity>,
    movements: List<PatrolMovementEntity>,
    permits: List<MovementPermitEntity>,
    mofaVisas: List<MofaVisaEntity>,
    foreigners: List<ForeignerResidentEntity>,
    refugees: List<RefugeeRecordEntity>,
    migrants: List<UndocumentedMigrantEntity>,
    citizenshipCases: List<CitizenshipCaseEntity>,
    agencies: List<TravelAgencyEntity>,
    correspondence: List<CorrespondenceEntity>,
    onAddReport: (String, String, String, String, String, String) -> Unit,
    onUpdateReportStatus: (SecurityReportEntity, String) -> Unit,
    onParseIntelligenceText: (String) -> Unit,
    onAddSeizure: (String, String, String) -> Unit,
    onAdvanceSeizure: (PassportSeizureEntity, String) -> Unit,
    onAddMovement: (String, String, String, String, Int) -> Unit,
    onMarkReturned: (PatrolMovementEntity, String) -> Unit,
    onAddPermit: (String, String, String, Int, String) -> Unit,
    onAddMofaVisa: (String, String, String, String, Int) -> Unit,
    onAddForeigner: (String, String, String, String, String, String) -> Unit,
    onAddRefugee: (String, String, String, String) -> Unit,
    onAddMigrant: (String, String, String) -> Unit,
    onAddCitizenshipCase: (String, String, String, Int) -> Unit,
    onAddAgency: (String, String, String) -> Unit,
    onAddCorrespondence: (String, String, String, String) -> Unit
) {
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var qrPermit by remember { mutableStateOf<MovementPermitEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SovereignModuleTab.entries) { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("module_tab_${tab.name}")
                )
            }
        }

        when (activeTab) {
            SovereignModuleTab.REPORTS -> ReportsTabContent(
                session = session,
                reports = reports,
                onAddReport = onAddReport,
                onUpdateStatus = onUpdateReportStatus,
                onParseText = onParseIntelligenceText
            )
            SovereignModuleTab.SEIZURES -> SeizuresTabContent(
                seizures = seizures,
                onAddSeizure = onAddSeizure,
                onAdvanceStage = onAdvanceSeizure
            )
            SovereignModuleTab.MOVEMENTS -> MovementsTabContent(
                movements = movements,
                onAddMovement = onAddMovement,
                onMarkReturned = onMarkReturned
            )
            SovereignModuleTab.PERMITS -> PermitsTabContent(
                permits = permits,
                onAddPermit = onAddPermit,
                onShowQr = { qrPermit = it }
            )
            SovereignModuleTab.MOFA -> MofaTabContent(
                visas = mofaVisas,
                onAddMofa = onAddMofaVisa
            )
            SovereignModuleTab.RESIDENTS -> ResidentsAndRefugeesTabContent(
                foreigners = foreigners,
                refugees = refugees,
                migrants = migrants,
                onAddForeigner = onAddForeigner,
                onAddRefugee = onAddRefugee,
                onAddMigrant = onAddMigrant
            )
            SovereignModuleTab.CITIZENSHIP -> CitizenshipSlaTabContent(
                cases = citizenshipCases,
                onAddCase = onAddCitizenshipCase
            )
            SovereignModuleTab.AGENCIES -> AgenciesAndCorrespondenceTabContent(
                agencies = agencies,
                correspondence = correspondence,
                onAddAgency = onAddAgency,
                onAddCorrespondence = onAddCorrespondence
            )
            SovereignModuleTab.PORTS -> PortsListTabContent()
        }
    }

    qrPermit?.let { permit ->
        val bmp = remember(permit.id) {
            SovereignExportEngine.generateQrBitmap(
                "YEM-PERMIT|${permit.permitCode}|${permit.delegationName}|${permit.authorizedRoute}|TO:${permit.validTo}"
            )
        }
        AlertDialog(
            onDismissRequest = { qrPermit = null },
            title = { Text("تصريح تنقل رسمي — QR") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (bmp != null) {
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = "رمز QR للتصريح", modifier = Modifier.size(190.dp))
                    }
                    Text(permit.delegationName, fontWeight = FontWeight.Bold)
                    Text("الرمز: ${permit.permitCode} | خط السير: ${permit.authorizedRoute}")
                    Text("صالح حتى: ${permit.validTo}")
                }
            },
            confirmButton = {
                TextButton(onClick = { qrPermit = null }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
private fun ReportsTabContent(
    session: OfficerSession,
    reports: List<SecurityReportEntity>,
    onAddReport: (String, String, String, String, String, String) -> Unit,
    onUpdateStatus: (SecurityReportEntity, String) -> Unit,
    onParseText: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var passport by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var intelText by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إدراج بلاغ أو تعميم أمني", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان البلاغ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("اسم المطلوب") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = passport, onValueChange = { passport = it }, label = { Text("رقم الجواز") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("التفاصيل والإجراء المطلوب") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onAddReport(title, target, passport, "تعميم أمني", "عالي", desc)
                            title = ""; target = ""; passport = ""; desc = ""
                        },
                        enabled = title.isNotBlank() && target.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("save_report_button")
                    ) { Text("اعتماد وإدراج البلاغ") }
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("استخلاص بلاغ من نص برقية أو بلاغ ميداني (أوفلاين)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = intelText, onValueChange = { intelText = it }, label = { Text("الصق نص البرقية هنا...") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = { onParseText(intelText); intelText = "" },
                        enabled = intelText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تحليل واستخلاص كبلاغ") }
                }
            }
        }
        items(reports, key = { it.id }) { rep ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rep.title, fontWeight = FontWeight.Bold)
                        Badge { Text(rep.status) }
                    }
                    Text("المطلوب: ${rep.targetName} | جواز: ${rep.targetPassportNumber.ifBlank { "-" }}")
                    if (rep.description.isNotBlank()) Text(rep.description, style = MaterialTheme.typography.bodyMedium)
                    if (session.role.canApproveBan && rep.status != "معتمد") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onUpdateStatus(rep, "معتمد") }) { Text("اعتماد الحظر", color = SovereignCrimson) }
                            TextButton(onClick = { onUpdateStatus(rep, "مرفوض") }) { Text("إغلاق") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeizuresTabContent(
    seizures: List<PassportSeizureEntity>,
    onAddSeizure: (String, String, String) -> Unit,
    onAdvanceStage: (PassportSeizureEntity, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تحرير محضر ضبط / احتجاز جواز", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم حامل الجواز") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("رقم الجواز") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("سبب الضبط / الاشتباه") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { onAddSeizure(name, pass, reason); name = ""; pass = ""; reason = "" },
                        enabled = name.isNotBlank() && pass.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تسجيل محضر ضبط") }
                }
            }
        }
        items(seizures, key = { it.id }) { sz ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${sz.recordNumber} — ${sz.travelerName}", fontWeight = FontWeight.Bold)
                        Badge { Text(sz.status) }
                    }
                    Text("جواز: ${sz.passportNumber} • المنفذ: ${sz.seizurePort}")
                    Text("السبب: ${sz.reason}", style = MaterialTheme.typography.bodyMedium)
                    if (sz.status != "تم التسليم") {
                        TextButton(onClick = { onAdvanceStage(sz, sz.travelerName) }) {
                            Text("نقل للمرحلة التالية")
                        }
                    } else {
                        Text("✓ سُلّم إلى: ${sz.recipientName} بتاريخ ${sz.deliveredAt}", color = SovereignEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementsTabContent(
    movements: List<PatrolMovementEntity>,
    onAddMovement: (String, String, String, String, Int) -> Unit,
    onMarkReturned: (PatrolMovementEntity, String) -> Unit
) {
    var vehicle by remember { mutableStateOf("طقم") }
    var plate by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var mission by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(6) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تسجيل خروج دورية ميدانية", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("طقم", "دراجة نارية", "أخرى").forEach { v ->
                            FilterChip(selected = vehicle == v, onClick = { vehicle = v }, label = { Text(v) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("رقم اللوحة") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = driver, onValueChange = { driver = it }, label = { Text("السائق / القائد") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = mission, onValueChange = { mission = it }, label = { Text("المهمة / الوجهة") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 6, 12, 24).forEach { h ->
                            FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("+${h}س") })
                        }
                    }
                    Button(
                        onClick = { onAddMovement(vehicle, plate, driver, mission, hours); plate = ""; driver = ""; mission = "" },
                        enabled = plate.isNotBlank() && driver.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تسجيل مغادرة") }
                }
            }
        }
        items(movements, key = { it.id }) { m ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${m.vehicleType} (${m.plateNumber}) — ${m.driverName}", fontWeight = FontWeight.Bold)
                        Badge { Text(m.status) }
                    }
                    Text("المهمة: ${m.mission.ifBlank { "-" }} • الخروج: ${m.departureTime}")
                    if (m.status == "في مهمة") {
                        TextButton(onClick = { onMarkReturned(m, "عودة نظامية") }) { Text("تأكيد العودة للمنفذ") }
                    } else {
                        Text("✓ عاد: ${m.actualReturnTime}", color = SovereignEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermitsTabContent(
    permits: List<MovementPermitEntity>,
    onAddPermit: (String, String, String, Int, String) -> Unit,
    onShowQr: (MovementPermitEntity) -> Unit
) {
    var delegation by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إصدار تصريح تنقل داخلي لوفد (مع QR)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = delegation, onValueChange = { delegation = it }, label = { Text("اسم الوفد / المنظمة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("خط السير المصرح") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("مقر الإقامة") }, singleLine = true, modifier = Modifier.weight(2f))
                        OutlinedTextField(value = count, onValueChange = { count = it }, label = { Text("الأفراد") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = { onAddPermit(delegation, route, place, count.toIntOrNull() ?: 1, ""); delegation = ""; route = ""; place = "" },
                        enabled = delegation.isNotBlank() && route.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إصدار تصريح + QR") }
                }
            }
        }
        items(permits, key = { it.id }) { p ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${p.permitCode} — ${p.delegationName}", fontWeight = FontWeight.Bold)
                        Text("المسار: ${p.authorizedRoute} (${p.passengersCount} مسافر)")
                    }
                    IconButton(onClick = { onShowQr(p) }) {
                        Icon(Icons.Default.QrCode2, contentDescription = "عرض QR التصريح")
                    }
                }
            }
        }
    }
}

@Composable
private fun MofaTabContent(visas: List<MofaVisaEntity>, onAddMofa: (String, String, String, String, Int) -> Unit) {
    var memo by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مذكرات الخارجية وتأشيرات المنظمات والرحلات الإنسانية", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("رقم المذكرة") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = org, onValueChange = { org = it }, label = { Text("المنظمة (UN/MSF)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("خط الرحلة / نوع المعاملة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { onAddMofa(memo, org, if (route.isNotBlank()) "مانيفست رحلة طيران" else "طلب تأشيرة دخول", route, 1); memo = ""; org = ""; route = "" },
                        enabled = memo.isNotBlank() && org.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حفظ مذكرة الخارجية") }
                }
            }
        }
        items(visas, key = { it.id }) { m ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${m.memoNumber} — ${m.organization}", fontWeight = FontWeight.Bold)
                    Text("${m.requestType} ${if (m.flightRoute.isNotBlank()) "• ${m.flightRoute}" else ""} (${m.beneficiariesCount} مستفيد)")
                }
            }
        }
    }
}

@Composable
private fun ResidentsAndRefugeesTabContent(
    foreigners: List<ForeignerResidentEntity>,
    refugees: List<RefugeeRecordEntity>,
    migrants: List<UndocumentedMigrantEntity>,
    onAddForeigner: (String, String, String, String, String, String) -> Unit,
    onAddRefugee: (String, String, String, String) -> Unit,
    onAddMigrant: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var docNo by remember { mutableStateOf("") }
    var nat by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var subType by remember { mutableStateOf("أجنبي مقيم") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("أجنبي مقيم", "لاجئ UNHCR", "مهاجر غير نظامي").forEach { t ->
                            FilterChip(selected = subType == t, onClick = { subType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الكامل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = docNo, onValueChange = { docNo = it }, label = { Text("رقم الإقامة / الوثيقة") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = nat, onValueChange = { nat = it }, label = { Text("الجنسية") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = extra, onValueChange = { extra = it }, label = { Text("الكفيل / المخيم / الإيواء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            when (subType) {
                                "أجنبي مقيم" -> onAddForeigner(docNo, name, docNo, nat, extra, "")
                                "لاجئ UNHCR" -> onAddRefugee(docNo, name, nat, extra)
                                else -> onAddMigrant(name, nat, extra)
                            }
                            name = ""; docNo = ""; nat = ""; extra = ""
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تسجيل $subType") }
                }
            }
        }
        items(foreigners, key = { it.id }) { f ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("إقامة ${f.residenceNumber} — ${f.fullName}", fontWeight = FontWeight.Bold)
                    Text("الجنسية: ${f.nationality} • الكفيل: ${f.employer}")
                }
            }
        }
        items(refugees, key = { it.id }) { r ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("لاجئ ${r.unhcrNumber} — ${r.fullName}", fontWeight = FontWeight.Bold)
                    Text("${r.nationality} • ${r.campOrCity}")
                }
            }
        }
        items(migrants, key = { it.id }) { m ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("غير نظامي: ${m.fullName}", fontWeight = FontWeight.Bold)
                    Text("${m.nationality} • الإيواء: ${m.shelterLocation}")
                }
            }
        }
    }
}

@Composable
private fun CitizenshipSlaTabContent(
    cases: List<CitizenshipCaseEntity>,
    onAddCase: (String, String, String, Int) -> Unit
) {
    var applicant by remember { mutableStateOf("") }
    var spouseNat by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf("طلب تجنيس أو إثبات جنسية") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قضايا الجنسية ومؤشر زمن الإنجاز (SLA)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = applicant, onValueChange = { applicant = it }, label = { Text("اسم مقدم الطلب") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = spouseNat, onValueChange = { spouseNat = it }, label = { Text("جنسية الطرف الآخر / ملاحظة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { onAddCase(serviceType, applicant, spouseNat, 14); applicant = ""; spouseNat = "" },
                        enabled = applicant.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("فتح ملف جنسية") }
                }
            }
        }
        items(cases, key = { it.id }) { c ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${c.caseNumber} — ${c.applicantName}", fontWeight = FontWeight.Bold)
                    Text("${c.serviceType} (الهدف: ${c.slaDaysTarget} يوم) • ${c.status}")
                }
            }
        }
        item {
            Text("دليل مؤشرات الأداء (15 خدمة قياسية)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(SovereignConstants.SLA_SERVICES) { sla ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sla.serviceName, fontWeight = FontWeight.SemiBold)
                        Text(sla.category, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${sla.targetDays} أيام", color = SovereignGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AgenciesAndCorrespondenceTabContent(
    agencies: List<TravelAgencyEntity>,
    correspondence: List<CorrespondenceEntity>,
    onAddAgency: (String, String, String) -> Unit,
    onAddCorrespondence: (String, String, String, String) -> Unit
) {
    var agName by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var entity by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إضافة وكالة سفر / شركة خاضعة للرقابة", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = agName, onValueChange = { agName = it }, label = { Text("اسم الوكالة أو الشركة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { onAddAgency(agName, "وكالة سفر وسياحة", "صنعاء"); agName = "" },
                        enabled = agName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تسجيل وكالة") }
                }
            }
        }
        items(agencies, key = { it.id }) { a ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(a.agencyName, fontWeight = FontWeight.Bold)
                    Text("التأشيرات المرتبطة: ${a.linkedVisasCount} • الصادرة: ${a.issuedCount} • المرتجعة: ${a.returnedCount}")
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تقييد مراسلة أو برقية رسمية", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("الموضوع") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = entity, onValueChange = { entity = it }, label = { Text("الجهة المرسلة / المستلمة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { onAddCorrespondence("وارد", subject, "", entity); subject = ""; entity = "" },
                        enabled = subject.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("أرشفة المراسلة") }
                }
            }
        }
        items(correspondence, key = { it.id }) { c ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${c.referenceNumber} (${c.direction}) — ${c.subject}", fontWeight = FontWeight.Bold)
                    Text("${c.senderEntity} -> ${c.recipientEntity} • ${c.gregorianDate}")
                }
            }
        }
    }
}

@Composable
private fun PortsListTabContent() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SovereignConstants.YEMEN_PORTS) { p ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text("النوع: ${p.type} • المحافظة: ${p.governorate}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Badge { Text(p.code) }
                }
            }
        }
    }
}
