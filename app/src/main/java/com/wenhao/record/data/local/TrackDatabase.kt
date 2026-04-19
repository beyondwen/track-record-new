package com.wenhao.record.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenhao.record.data.local.decision.DecisionDao
import com.wenhao.record.data.local.decision.DecisionEventEntity
import com.wenhao.record.data.local.decision.DecisionFeedbackEntity
import com.wenhao.record.data.local.history.HistoryDao
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.stream.AnalysisCursorEntity
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.local.stream.UploadCursorEntity

@Database(
    entities = [
        HistoryRecordEntity::class,
        HistoryPointEntity::class,
        DecisionEventEntity::class,
        DecisionFeedbackEntity::class,
        RawLocationPointEntity::class,
        AnalysisSegmentEntity::class,
        StayClusterEntity::class,
        AnalysisCursorEntity::class,
        UploadCursorEntity::class,
    ],
    version = 10,
    exportSchema = true
)
abstract class TrackDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    abstract fun decisionDao(): DecisionDao

    abstract fun continuousTrackDao(): ContinuousTrackDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema unchanged; this migration formalizes versioned upgrades so history is preserved.
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add WGS-84 coordinate columns for accurate distance calculation
                database.execSQL("ALTER TABLE auto_track_point ADD COLUMN wgs84Latitude REAL")
                database.execSQL("ALTER TABLE auto_track_point ADD COLUMN wgs84Longitude REAL")
                database.execSQL("ALTER TABLE history_point ADD COLUMN wgs84Latitude REAL")
                database.execSQL("ALTER TABLE history_point ADD COLUMN wgs84Longitude REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE auto_track_point ADD COLUMN altitudeMeters REAL")
                database.execSQL("ALTER TABLE history_point ADD COLUMN altitudeMeters REAL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `decision_event` (
                        `eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampMillis` INTEGER NOT NULL,
                        `phase` TEXT NOT NULL,
                        `isRecording` INTEGER NOT NULL,
                        `startScore` REAL NOT NULL,
                        `stopScore` REAL NOT NULL,
                        `finalDecision` TEXT NOT NULL,
                        `featureJson` TEXT NOT NULL,
                        `gpsQualityPass` INTEGER NOT NULL DEFAULT 0,
                        `motionEvidencePass` INTEGER NOT NULL DEFAULT 0,
                        `frequentPlaceClearPass` INTEGER NOT NULL DEFAULT 0,
                        `feedbackEligible` INTEGER NOT NULL DEFAULT 0,
                        `feedbackBlockedReason` TEXT
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `decision_feedback` (
                        `feedbackId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` INTEGER NOT NULL,
                        `feedbackType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE decision_event ADD COLUMN gpsQualityPass INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE decision_event ADD COLUMN motionEvidencePass INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE decision_event ADD COLUMN frequentPlaceClearPass INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE decision_event ADD COLUMN feedbackEligible INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE decision_event ADD COLUMN feedbackBlockedReason TEXT"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN startSource TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN stopSource TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN manualStartAt INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN manualStopAt INTEGER"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `raw_location_point` (
                        `pointId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampMillis` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `accuracyMeters` REAL,
                        `altitudeMeters` REAL,
                        `speedMetersPerSecond` REAL,
                        `bearingDegrees` REAL,
                        `provider` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `isMock` INTEGER NOT NULL,
                        `wifiFingerprintDigest` TEXT,
                        `activityType` TEXT,
                        `activityConfidence` REAL,
                        `samplingTier` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_segment` (
                        `segmentId` INTEGER NOT NULL,
                        `startPointId` INTEGER NOT NULL,
                        `endPointId` INTEGER NOT NULL,
                        `startTimestamp` INTEGER NOT NULL,
                        `endTimestamp` INTEGER NOT NULL,
                        `segmentType` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `distanceMeters` REAL NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `avgSpeedMetersPerSecond` REAL NOT NULL,
                        `maxSpeedMetersPerSecond` REAL NOT NULL,
                        `analysisVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`segmentId`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stay_cluster` (
                        `stayId` INTEGER NOT NULL,
                        `segmentId` INTEGER NOT NULL,
                        `centerLat` REAL NOT NULL,
                        `centerLng` REAL NOT NULL,
                        `radiusMeters` REAL NOT NULL,
                        `arrivalTime` INTEGER NOT NULL,
                        `departureTime` INTEGER NOT NULL,
                        `confidence` REAL NOT NULL,
                        `analysisVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`stayId`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_cursor` (
                        `cursorId` INTEGER NOT NULL,
                        `lastAnalyzedPointId` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cursorId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `auto_track_point`")
                database.execSQL("DROP TABLE IF EXISTS `auto_track_session`")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `upload_cursor` (
                        `cursorType` TEXT NOT NULL,
                        `lastUploadedId` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cursorType`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: TrackDatabase? = null

        fun getInstance(context: Context): TrackDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "track_record.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                    )
                    .build()
                    .also { instance = it }
            }
        }

        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
