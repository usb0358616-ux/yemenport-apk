package com.example.yemenport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TravelerEntity::class,
        TravelEntity::class,
        SecurityReportEntity::class,
        PatrolMovementEntity::class,
        PassportSeizureEntity::class,
        UndocumentedMigrantEntity::class,
        RefugeeRecordEntity::class,
        ForeignerResidentEntity::class,
        MovementPermitEntity::class,
        CitizenshipCaseEntity::class,
        TravelAgencyEntity::class,
        CorrespondenceEntity::class,
        MofaVisaEntity::class,
        PortOfficerEntity::class,
        AuditLogEntity::class,
        LearnedPatternEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(BorderTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun borderDao(): BorderDao

    companion object {
        const val DATABASE_NAME: String = "yemen_borders_sovereign_v1.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE travelers ADD COLUMN securityFlags TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE travelers ADD COLUMN mergeRecords TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE travelers ADD COLUMN extraAttributes TEXT NOT NULL DEFAULT '{}'")

                db.execSQL("ALTER TABLE audit_logs ADD COLUMN severityLevel TEXT NOT NULL DEFAULT 'INFO'")
                db.execSQL("ALTER TABLE audit_logs ADD COLUMN metadata TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE audit_logs ADD COLUMN recordedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_entityType ON audit_logs(entityType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_createdAt ON audit_logs(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS travels (
                        id TEXT NOT NULL PRIMARY KEY,
                        travelerId TEXT NOT NULL,
                        travelerName TEXT NOT NULL,
                        passportNumber TEXT NOT NULL,
                        portName TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        destination TEXT NOT NULL,
                        travelAllowed TEXT NOT NULL,
                        clearanceStatus TEXT NOT NULL DEFAULT 'ALLOWED',
                        vettingStatus TEXT NOT NULL,
                        officerName TEXT NOT NULL,
                        flags TEXT NOT NULL,
                        metadata TEXT NOT NULL,
                        crossedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_travels_passportNumber ON travels(passportNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_travels_travelerId ON travels(travelerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_travels_portName ON travels(portName)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
