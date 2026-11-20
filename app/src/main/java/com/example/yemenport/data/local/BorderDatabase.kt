package com.example.yemenport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TravelerEntity::class,
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
    version = 1,
    exportSchema = false
)
abstract class BorderDatabase : RoomDatabase() {
    abstract fun borderDao(): BorderDao

    companion object {
        @Volatile
        private var INSTANCE: BorderDatabase? = null

        fun getInstance(context: Context): BorderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BorderDatabase::class.java,
                    "yemen_borders_sovereign_v1.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
