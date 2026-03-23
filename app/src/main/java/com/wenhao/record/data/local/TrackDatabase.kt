package com.wenhao.record.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenhao.record.data.local.auto.AutoTrackDao
import com.wenhao.record.data.local.auto.AutoTrackPointEntity
import com.wenhao.record.data.local.auto.AutoTrackSessionEntity
import com.wenhao.record.data.local.history.HistoryDao
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity

@Database(
    entities = [
        AutoTrackSessionEntity::class,
        AutoTrackPointEntity::class,
        HistoryRecordEntity::class,
        HistoryPointEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class TrackDatabase : RoomDatabase() {

    abstract fun autoTrackDao(): AutoTrackDao

    abstract fun historyDao(): HistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema unchanged; this migration formalizes versioned upgrades so history is preserved.
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
