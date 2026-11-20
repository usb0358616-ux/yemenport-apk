package com.example.yemenport.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.yemenport.core.BiometricAuthentication
import com.example.yemenport.core.OfficerRole
import com.example.yemenport.core.SovereignConstants
import com.example.yemenport.data.local.AuditLogEntity
import com.example.yemenport.data.local.AuditSeverity
import com.example.yemenport.data.local.PortOfficerEntity
import com.example.yemenport.ui.OfficerSession
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald

@Composable
fun SettingsAndAuditScreen(
    session: OfficerSession,
    officers: List<PortOfficerEntity>,
    auditLogs: List<AuditLogEntity>,
    onUpdateSession: (
        officerName: String,
        role: OfficerRole,
        assignedPort: String,
        biometricEnabled: Boolean,
        highContrastMode: Boolean
    ) -> Unit,
    onAddOfficer: (String, String, String, String, Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var officerName by remember(session.officerName) { mutableStateOf(session.officerName) }
    var selectedRole by remember(session.role) { mutableStateOf(session.role) }
    var selectedPort by remember(session.assignedPort) { mutableStateOf(session.assignedPort) }

    var newRank by remember { mutableStateOf("نقيب") }
    var newOffName by remember { mutableStateOf("") }
    var newOffDuty by remember { mutableStateOf(true) }

    val biometricAuth = remember { BiometricAuthentication() }

    fun verifyBiometricAndToggle(enable: Boolean) {
        val activity = context as? FragmentActivity
        if (!enable || activity == null || !biometricAuth.canAuthenticate(context)) {
            onUpdateSession(officerName, selectedRole, selectedPort, enable, session.highContrastMode)
            return
        }

        biometricAuth.authenticate(
            activity = activity,
            title = "تأكيد الهوية بالبصمة",
            subtitle = "تفعيل القفل التلقائي عند الفتح وبعد الخمول",
            onSuccess = {
                onUpdateSession(officerName, selectedRole, selectedPort, true, session.highContrastMode)
                Toast.makeText(context, "تم تفعيل قفل البصمة الميداني (عند الفتح وبعد الخمول)", Toast.LENGTH_SHORT).show()
            },
            onFailed = {
                Toast.makeText(context, "لم تتطابق البصمة — حاول مجدداً", Toast.LENGTH_SHORT).show()
            },
            onError = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("الإعدادات والصلاحيات وسجل التدقيق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // Active Officer Profile & Role Assignment
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ملف الضابط النشط وصلاحيات الدور (RBAC)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = officerName,
                        onValueChange = { officerName = it },
                        label = { Text("الاسم والرتبة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("الدور الوظيفي:", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(OfficerRole.entries) { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role.arabicTitle) }
                            )
                        }
                    }

                    Text("المنفذ المعين:", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SovereignConstants.PORT_NAMES.take(10)) { p ->
                            FilterChip(
                                selected = selectedPort == p,
                                onClick = { selectedPort = p },
                                label = { Text(p) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الوضع الداكن عالي التباين (عمل ليلي)")
                        Switch(
                            checked = session.highContrastMode,
                            onCheckedChange = { dark ->
                                onUpdateSession(officerName, selectedRole, selectedPort, session.biometricEnabled, dark)
                            },
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تفعيل التحقق بالبصمة (BiometricPrompt)")
                        Switch(
                            checked = session.biometricEnabled,
                            onCheckedChange = { verifyBiometricAndToggle(it) },
                            modifier = Modifier.testTag("biometric_switch")
                        )
                    }

                    Button(
                        onClick = {
                            onUpdateSession(officerName, selectedRole, selectedPort, session.biometricEnabled, session.highContrastMode)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_settings_button")
                    ) {
                        Text("حفظ إعدادات الضابط والمنفذ")
                    }
                }
            }
        }

        // RBAC Matrix Summary
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("صلاحيات الدور الحالي (${selectedRole.arabicTitle}):", fontWeight = FontWeight.Bold)
                    Text("• إدارة الضباط والمستخدمين: ${if (selectedRole.canManageUsers) "مسموح ✓" else "مقيد"}")
                    Text("• اعتماد الحظر والتعميمات: ${if (selectedRole.canApproveBan) "مسموح ✓" else "مقيد"}")
                    Text("• الاطلاع على سجل التدقيق: ${if (selectedRole.canViewAuditLogs) "مسموح ✓" else "مقيد"}")
                    Text("• حذف السجلات: ${if (selectedRole.canDeleteRecords) "مسموح ✓" else "مقيد"}")
                }
            }
        }

        // Officers Directory (Managers only)
        if (session.role.canManageUsers) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("إضافة ضابط منفذ", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = newRank, onValueChange = { newRank = it }, label = { Text("الرتبة") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = newOffName, onValueChange = { newOffName = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.weight(2f))
                        }
                        Button(
                            onClick = {
                                onAddOfficer(newRank, newOffName, selectedPort, OfficerRole.FIELD_OFFICER.arabicTitle, newOffDuty)
                                newOffName = ""
                            },
                            enabled = newOffName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تعيين ضابط") }
                    }
                }
            }
            items(officers, key = { it.id }) { o ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${o.rank} ${o.fullName} — ${o.assignedPort}")
                        Badge { Text(if (o.dutyOfficer) "مناوب" else o.role) }
                    }
                }
            }
        }

        // Immutable Audit Trail
        if (session.role.canViewAuditLogs) {
            item {
                Text("سجل التدقيق غير القابل للتعديل (${auditLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(auditLogs.take(50), key = { it.id }) { log ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${log.action} [${log.severityLevel.arabicLabel}]",
                                fontWeight = FontWeight.Bold,
                                color = if (log.severityLevel == AuditSeverity.CRITICAL) SovereignCrimson else SovereignEmerald
                            )
                            Text(log.timestampIso.take(16), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(log.details, style = MaterialTheme.typography.bodyMedium)
                        Text("${log.officerName} (${log.officerRole}) • ${log.portName}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Cloud Sync & Sign Out Actions
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onSyncNow, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("مزامنة الآن")
                }
                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignCrimson),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("تسجيل الخروج")
                }
            }
        }
    }
}
