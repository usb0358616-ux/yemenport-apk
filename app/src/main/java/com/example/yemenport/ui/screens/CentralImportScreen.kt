package com.example.yemenport.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.yemenport.domain.importengine.*
import com.example.yemenport.ui.theme.SovereignCrimson
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold

@Composable
fun CentralImportScreen(
    stagedFiles: List<StagedFileItem>,
    importProgress: Pair<Int, String>?,
    stagedBatch: StagedImportBatch?,
    lastCommitResult: CommitImportResult?,
    onStageUris: (List<android.net.Uri>) -> Unit,
    onRemoveStagedFile: (String) -> Unit,
    onClearAll: () -> Unit,
    onAnalyzeFiles: () -> Unit,
    onChangeTargetCategory: (ImportTargetCategory) -> Unit,
    onUpdateRow: (EditableImportRow) -> Unit,
    onCommitBatch: () -> Unit
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onStageUris(uris)
    }

    var editingRow by remember { mutableStateOf<EditableImportRow?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("مركز الاستيراد والمعالجة الموحد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "يدعم استقبال عدة ملفات دفعة واحدة (.xlsx, .csv, .pdf, صور) مع تصنيف تلقائي، تنقية عربية، مطابقة أمنية، ومعاينة تفاعلية قبل الحفظ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "text/csv",
                                "text/plain",
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "image/*"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("pick_import_files_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اختيار ملفات")
                }

                if (stagedFiles.isNotEmpty() || stagedBatch != null) {
                    OutlinedButton(
                        onClick = onClearAll,
                        modifier = Modifier.height(50.dp).testTag("clear_staged_files_button")
                    ) {
                        Text("مسح")
                    }
                }
            }
        }

        // Staged Files List
        if (stagedFiles.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الملفات المجهزة للتحليل (${stagedFiles.size})", fontWeight = FontWeight.Bold)
                        stagedFiles.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• ${file.name} (${file.extension} • ${file.readableSize})", modifier = Modifier.weight(1f))
                                IconButton(onClick = { onRemoveStagedFile(file.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "إزالة الملف")
                                }
                            }
                        }

                        Button(
                            onClick = onAnalyzeFiles,
                            enabled = importProgress == null,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("analyze_staged_files_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("بدء التحليل والتصنيف والمطابقة الأمنية")
                        }
                    }
                }
            }
        }

        if (importProgress != null) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(importProgress.second, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(
                            progress = { (importProgress.first / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (lastCommitResult != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SovereignEmerald.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("✓ اكتمل الترحيل النهائي بنجاح", fontWeight = FontWeight.Bold, color = SovereignEmerald)
                        Text("الوحدة: ${lastCommitResult.targetCategory.arabicTitle}")
                        Text("السجلات المرحّلة: ${lastCommitResult.committedCount} | دمج ذكي: ${lastCommitResult.smartMergedCount} | تنبيهات المطلوبين: ${lastCommitResult.watchlistHits}")
                    }
                }
            }
        }

        // Interactive Preview & Review Stage before final commit
        if (stagedBatch != null) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("معاينة ومراجعة ذكية قبل الترحيل (${stagedBatch.rows.size} سجل)", fontWeight = FontWeight.Bold)
                        Text("اختر أو عدّل الوجهة التلقائية:", style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ImportTargetCategory.entries) { cat ->
                                FilterChip(
                                    selected = stagedBatch.targetCategory == cat,
                                    onClick = { onChangeTargetCategory(cat) },
                                    label = { Text(cat.arabicTitle) }
                                )
                            }
                        }

                        stagedBatch.fileAnalyses.forEach { analysis ->
                            Text(
                                "ملف ${analysis.fileName}: ثقة ${analysis.confidencePercent}% (${analysis.rowCount} صف) — ${analysis.detectedCategory.arabicTitle}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SovereignGold
                            )
                        }

                        Button(
                            onClick = onCommitBatch,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("commit_import_batch_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("اعتماد وترحيل (${stagedBatch.rows.count { it.selected }}) سجل لقاعدة البيانات")
                        }
                    }
                }
            }

            items(stagedBatch.rows.take(150), key = { it.rowId }) { row ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = row.selected,
                                    onCheckedChange = { onUpdateRow(row.copy(selected = it)) }
                                )
                                Column {
                                    Text(row.fullName, fontWeight = FontWeight.Bold)
                                    Text("جواز: ${row.passportNumber.ifBlank { "-" }} • ${row.nationality} • ${row.agencyOrOrg.ifBlank { row.sourceFile }}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            IconButton(onClick = { editingRow = row }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل السجل قبل الترحيل")
                            }
                        }
                        if (row.watchlistWarning.isNotBlank()) {
                            Text("🚨 ${row.watchlistWarning}", color = SovereignCrimson, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    editingRow?.let { row ->
        var editName by remember(row.rowId) { mutableStateOf(row.fullName) }
        var editPass by remember(row.rowId) { mutableStateOf(row.passportNumber) }
        var editNat by remember(row.rowId) { mutableStateOf(row.nationality) }
        var editExp by remember(row.rowId) { mutableStateOf(row.expiryDate) }
        var editAgency by remember(row.rowId) { mutableStateOf(row.agencyOrOrg) }

        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text("تعديل سجل قبل الحفظ النهائي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("الاسم") }, singleLine = true)
                    OutlinedTextField(value = editPass, onValueChange = { editPass = it }, label = { Text("رقم الجواز") }, singleLine = true)
                    OutlinedTextField(value = editNat, onValueChange = { editNat = it }, label = { Text("الجنسية") }, singleLine = true)
                    OutlinedTextField(value = editExp, onValueChange = { editExp = it }, label = { Text("تاريخ الانتهاء") }, singleLine = true)
                    OutlinedTextField(value = editAgency, onValueChange = { editAgency = it }, label = { Text("الوكالة / المنظمة") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateRow(
                        row.copy(
                            fullName = editName,
                            passportNumber = editPass,
                            nationality = editNat,
                            expiryDate = editExp,
                            agencyOrOrg = editAgency
                        )
                    )
                    editingRow = null
                }) { Text("تحديث") }
            },
            dismissButton = {
                TextButton(onClick = { editingRow = null }) { Text("إلغاء") }
            }
        )
    }
}
