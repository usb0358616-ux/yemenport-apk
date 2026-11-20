package com.example.yemenport.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yemenport.core.SovereignConstants
import com.example.yemenport.data.local.SecurityReportEntity
import com.example.yemenport.data.local.TravelerEntity
import com.example.yemenport.domain.security.WatchlistCheckSummary
import com.example.yemenport.ui.DashboardKpis
import com.example.yemenport.ui.OfficerSession
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold
import com.example.yemenport.ui.theme.SovereignNavy

@Composable
fun DashboardScreen(
    session: OfficerSession,
    kpis: DashboardKpis,
    criticalAlert: WatchlistCheckSummary?,
    travelers: List<TravelerEntity>,
    reports: List<SecurityReportEntity>,
    isSyncing: Boolean,
    onDismissAlert: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToTravelers: () -> Unit,
    onNavigateToReports: () -> Unit,
    onSyncNow: () -> Unit
) {
    var globalQuery by remember { mutableStateOf("") }
    val matchingTravelers = remember(globalQuery, travelers) {
        if (globalQuery.trim().length < 2) emptyList()
        else {
            val q = globalQuery.trim()
            travelers.filter {
                it.fullName.contains(q, ignoreCase = true) ||
                    it.passportName.contains(q, ignoreCase = true) ||
                    it.idNumber.contains(q, ignoreCase = true) ||
                    it.portName.contains(q, ignoreCase = true)
            }.take(8)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Critical Security Alert Banner
        if (criticalAlert != null && criticalAlert.isCritical) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SovereignCrimson),
                    modifier = Modifier.fillMaxWidth().testTag("critical_watchlist_banner")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "تنبيه أمني", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "تنبيه أمني حرج (${criticalAlert.hits.size} تطابق)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = onDismissAlert) {
                                Text("إغلاق", color = Color.White)
                            }
                        }
                        Text(
                            "الاستعلام: ${criticalAlert.queryName} — جواز: ${criticalAlert.queryPassport}",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        criticalAlert.hits.take(3).forEach { hit ->
                            Text(
                                "• [${hit.source.arabicLabel}] ${hit.matchedName} (${hit.matchType}): ${hit.reason}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Officer Status Header
        item {
            ElevatedCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                session.hqCenterName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${session.officerName} • ${session.role.arabicTitle}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "المنفذ النشط: ${session.assignedPort}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        FilledTonalButton(
                            onClick = onSyncNow,
                            enabled = !isSyncing,
                            modifier = Modifier.testTag("dashboard_sync_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = "مزامنة سحابية")
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (kpis.unsyncedCount > 0) "مزامنة (${kpis.unsyncedCount})" else "مزامن")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("أوفلاين 100% (Room SQLite)") },
                            leadingIcon = {
                                Icon(Icons.Default.OfflinePin, contentDescription = null, tint = SovereignEmerald, modifier = Modifier.size(18.dp))
                            }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("${SovereignConstants.YEMEN_PORTS.size} منفذاً سيادياً") }
                        )
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    title = "مسح جواز OCR",
                    subtitle = "كاميرا + MRZ + مطابقة",
                    icon = Icons.Default.DocumentScanner,
                    tint = SovereignNavy,
                    modifier = Modifier.weight(1f).testTag("quick_scan_button"),
                    onClick = onNavigateToScanner
                )
                QuickActionTile(
                    title = "استيراد كشف",
                    subtitle = "Excel / CSV / PDF",
                    icon = Icons.Default.UploadFile,
                    tint = SovereignGold,
                    modifier = Modifier.weight(1f).testTag("quick_import_button"),
                    onClick = onNavigateToImport
                )
                QuickActionTile(
                    title = "البلاغات",
                    subtitle = "${kpis.activeReportsCount} نشط",
                    icon = Icons.Default.GppMaybe,
                    tint = SovereignCrimson,
                    modifier = Modifier.weight(1f).testTag("quick_reports_button"),
                    onClick = onNavigateToReports
                )
            }
        }

        // Global Field Search
        item {
            OutlinedTextField(
                value = globalQuery,
                onValueChange = { globalQuery = it },
                label = { Text("بحث أمني شامل (الاسم، رقم الجواز، المنفذ)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (globalQuery.isNotEmpty()) {
                        IconButton(onClick = { globalQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_search_input")
            )
        }

        if (matchingTravelers.isNotEmpty()) {
            items(matchingTravelers, key = { it.id }) { t ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToTravelers() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("جواز: ${t.idNumber.ifBlank { "-" }} • ${t.country} • ${t.portName}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Surface(
                            color = if (t.travelAllowed == "لا") SovereignCrimson.copy(alpha = 0.15f) else SovereignEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (t.travelAllowed == "لا") "ممنوع" else "مسموح",
                                color = if (t.travelAllowed == "لا") SovereignCrimson else SovereignEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Real-Time KPIs Grid (2x3)
        item {
            Text("المؤشرات الميدانية الحية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiStatCard("إجمالي المسافرين", kpis.totalTravelers.toString(), Icons.Default.People, SovereignNavy, Modifier.weight(1f))
                    KpiStatCard("مسموح بالعبور", kpis.allowedCount.toString(), Icons.Default.CheckCircle, SovereignEmerald, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiStatCard("ممنوع / مرتجع", kpis.blockedOrReturnedCount.toString(), Icons.Default.Block, SovereignCrimson, Modifier.weight(1f))
                    KpiStatCard("البلاغات النشطة", kpis.activeReportsCount.toString(), Icons.Default.ReportProblem, SovereignGold, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiStatCard("جوازات مضبوطة", kpis.seizedPassportsCount.toString(), Icons.Default.Badge, SovereignCrimson, Modifier.weight(1f))
                    KpiStatCard("دوريات نشطة / تصاريح", "${kpis.activePatrolsCount} / ${kpis.activePermitsCount}", Icons.Default.LocalPolice, SovereignNavy, Modifier.weight(1f))
                }
            }
        }

        // Visual Operations Breakdown Chart (Canvas)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("التوزيع العملياتي الفوري للسجلات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val bars = listOf(
                        Triple("مسموح", kpis.allowedCount, SovereignEmerald),
                        Triple("ممنوع", kpis.blockedOrReturnedCount, SovereignCrimson),
                        Triple("بلاغات", kpis.activeReportsCount, SovereignGold),
                        Triple("مضبوطات", kpis.seizedPassportsCount, Color(0xFF9333EA)),
                        Triple("خارجية", kpis.mofaRequestsCount, SovereignNavy)
                    )
                    val maxVal = (bars.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        val slotWidth = size.width / bars.size
                        val barWidth = slotWidth * 0.52f
                        bars.forEachIndexed { idx, (_, value, color) ->
                            val ratio = value.toFloat() / maxVal.toFloat()
                            val barHeight = (size.height - 16f) * ratio.coerceAtLeast(0.05f)
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            drawRect(
                                color = color,
                                topLeft = Offset(left, size.height - barHeight),
                                size = Size(barWidth, barHeight)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        bars.forEach { (label, count, color) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(count.toString(), fontWeight = FontWeight.Bold, color = color)
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Latest Active Security Directives
        if (reports.isNotEmpty()) {
            item {
                Text("أحدث البلاغات والتعميمات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(reports.take(3), key = { it.id }) { rep ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onNavigateToReports() }) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rep.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("المستهدف: ${rep.targetName} • جواز: ${rep.targetPassportNumber.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Badge { Text(rep.status) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.heightIn(min = 96.dp).clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint)
            }
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
            }
            Icon(icon, contentDescription = label, tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
        }
    }
}
