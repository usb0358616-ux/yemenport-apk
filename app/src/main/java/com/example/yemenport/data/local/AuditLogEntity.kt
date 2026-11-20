package com.example.yemenport.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class AuditSeverity(val arabicLabel: String) {
    INFO("معلومات"),
    WARN("تحذير"),
    CRITICAL("حرج");

    companion object {
        fun fromCode(raw: String?): AuditSeverity {
            if (raw.isNullOrBlank()) return INFO
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: INFO
        }
    }
}

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["entityType"]),
        Index(value = ["createdAt"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val entityType: String,
    val entityId: String = "",
    val details: String,
    val officerName: String,
    val officerRole: String,
    val portName: String = "",
    val timestampIso: String,
    val severityLevel: AuditSeverity = AuditSeverity.INFO,
    val metadata: Map<String, String> = emptyMap(),
    val recordedAt: Instant = Instant.now(),
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
