package com.wenhao.record.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class TrackDatabase : RoomDatabase() {

    abstract fun autoTrackDao(): AutoTrackDao

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: TrackDatabase? = null

        fun getInstance(context: Context): TrackDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "track_record.db"
                ).build()
                    .also { instance = it }
            }
        }
    }
}
