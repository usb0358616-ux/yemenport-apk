package com.example.yemenport.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class TravelClearanceStatus(val arabicLabel: String) {
    ALLOWED("مسموح"),
    BLOCKED("ممنوع"),
    PENDING_REVIEW("قيد المراجعة"),
    RETURNED("مُرتجع");

    companion object {
        fun fromCode(raw: String?): TravelClearanceStatus {
            if (raw.isNullOrBlank()) return ALLOWED
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: when (raw.trim()) {
                    "لا", "ممنوع" -> BLOCKED
                    "مرتجع", "مُرتجع" -> RETURNED
                    "قيد المراجعة" -> PENDING_REVIEW
                    else -> ALLOWED
                }
        }

        fun fromTravelAllowedFlag(allowed: String?): TravelClearanceStatus {
            return if (allowed?.trim() == "لا") BLOCKED else ALLOWED
        }
    }
}

@Entity(
    tableName = "travels",
    indices = [
        Index(value = ["passportNumber"]),
        Index(value = ["travelerId"]),
        Index(value = ["portName"])
    ]
)
data class TravelEntity(
    @PrimaryKey val id: String,
    val travelerId: String,
    val travelerName: String,
    val passportNumber: String,
    val portName: String,
    val direction: String = "مغادرة", // دخول | مغادرة | عبور
    val destination: String = "",
    val travelAllowed: String = "نعم",
    val clearanceStatus: TravelClearanceStatus = TravelClearanceStatus.fromTravelAllowedFlag(travelAllowed),
    val vettingStatus: String = "سليم",
    val officerName: String = "",
    val flags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val crossedAt: Instant = Instant.now(),
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
