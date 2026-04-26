package com.wenhao.record.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenhao.record.data.local.history.HistoryDao
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.stream.AnalysisCursorEntity
import com.wenhao.record.data.local.stream.AnalysisSegmentEntity
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
import com.wenhao.record.data.local.stream.StayClusterEntity
import com.wenhao.record.data.local.stream.SyncOutboxDao
import com.wenhao.record.data.local.stream.SyncOutboxEntity
import com.wenhao.record.data.local.stream.TodayDisplayPointEntity
import com.wenhao.record.data.local.stream.TodaySessionDao
import com.wenhao.record.data.local.stream.TodaySessionEntity
import com.wenhao.record.data.local.stream.TodaySessionPointEntity
import com.wenhao.record.data.local.stream.TodayTrackDisplayDao
import com.wenhao.record.data.local.stream.UploadCursorEntity

@Database(
    entities = [
        HistoryRecordEntity::class,
        HistoryPointEntity::class,
        RawLocationPointEntity::class,
        AnalysisSegmentEntity::class,
        StayClusterEntity::class,
        AnalysisCursorEntity::class,
        UploadCursorEntity::class,
        TodayDisplayPointEntity::class,
        TodaySessionEntity::class,
        TodaySessionPointEntity::class,
        SyncOutboxEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class TrackDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    abstract fun continuousTrackDao(): ContinuousTrackDao

    abstract fun todayTrackDisplayDao(): TodayTrackDisplayDao

    abstract fun todaySessionDao(): TodaySessionDao

    abstract fun syncOutboxDao(): SyncOutboxDao

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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `decision_event`")
                database.execSQL("DROP TABLE IF EXISTS `decision_feedback`")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `today_display_point` (
                        `timestampMillis` INTEGER NOT NULL,
                        `dayStartMillis` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `accuracyMeters` REAL,
                        `altitudeMeters` REAL,
                        PRIMARY KEY(`timestampMillis`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_location_point_timestampMillis` ON `raw_location_point` (`timestampMillis`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_location_point_pointId_timestampMillis` ON `raw_location_point` (`pointId`, `timestampMillis`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_analysis_segment_startTimestamp` ON `analysis_segment` (`startTimestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_analysis_segment_endTimestamp` ON `analysis_segment` (`endTimestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stay_cluster_segmentId` ON `stay_cluster` (`segmentId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_display_point_dayStartMillis_timestampMillis` ON `today_display_point` (`dayStartMillis`, `timestampMillis`)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_outbox` (
                        `outboxId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `itemKey` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_itemType_itemKey` ON `sync_outbox` (`itemType`, `itemKey`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_status_updatedAt` ON `sync_outbox` (`status`, `updatedAt`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `today_session` (
                        `sessionId` TEXT NOT NULL,
                        `dayStartMillis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `lastPointAt` INTEGER,
                        `endedAt` INTEGER,
                        `lastSyncedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        `phase` TEXT,
                        `anchorPointRef` INTEGER,
                        `recoveredFromRemote` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_dayStartMillis_status_startedAt` ON `today_session` (`dayStartMillis`, `status`, `startedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_status_dayStartMillis` ON `today_session` (`status`, `dayStartMillis`)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `today_session_point` (
                        `sessionId` TEXT NOT NULL,
                        `pointId` INTEGER NOT NULL,
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
                        `samplingTier` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `syncState` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`, `pointId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_point_sessionId_timestampMillis` ON `today_session_point` (`sessionId`, `timestampMillis`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_today_session_point_sessionId_syncState_timestampMillis` ON `today_session_point` (`sessionId`, `syncState`, `timestampMillis`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN sourceSessionId TEXT"
                )
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN dateKey INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'"
                )
                database.execSQL(
                    "ALTER TABLE history_record ADD COLUMN version INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "UPDATE history_record SET dateKey = timestamp - (timestamp % 86400000) WHERE dateKey = 0"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_history_record_dateKey_timestamp` ON `history_record` (`dateKey`, `timestamp`)")
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
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
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
