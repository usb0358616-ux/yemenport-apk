package com.example.yemenport.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class MergeHistoryEntry(
    val timestampIso: String,
    val officerName: String,
    val portName: String,
    val changedFields: List<String> = emptyList(),
    val note: String = ""
)

class BorderTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String {
        return json.encodeToString(value ?: emptyMap())
    }

    @TypeConverter
    fun toStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    @TypeConverter
    fun fromMergeHistoryList(value: List<MergeHistoryEntry>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toMergeHistoryList(raw: String?): List<MergeHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<MergeHistoryEntry>>(raw) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toLongList(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Long>>(raw) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromTravelClearanceStatus(status: TravelClearanceStatus?): String =
        (status ?: TravelClearanceStatus.ALLOWED).name

    @TypeConverter
    fun toTravelClearanceStatus(raw: String?): TravelClearanceStatus =
        TravelClearanceStatus.fromCode(raw)

    @TypeConverter
    fun fromAuditSeverity(severity: AuditSeverity?): String =
        (severity ?: AuditSeverity.INFO).name

    @TypeConverter
    fun toAuditSeverity(raw: String?): AuditSeverity =
        AuditSeverity.fromCode(raw)
}
