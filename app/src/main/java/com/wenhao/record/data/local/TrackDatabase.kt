package com.wenhao.record.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wenhao.record.data.local.history.HistoryDao
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.RawLocationPointEntity
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
        RawLocationPointEntity::class,
        UploadCursorEntity::class,
        TodayDisplayPointEntity::class,
        TodaySessionEntity::class,
        TodaySessionPointEntity::class,
        SyncOutboxEntity::class,
        HistoryRecordEntity::class,
        HistoryPointEntity::class,
    ],
    version = 17,
    exportSchema = true,
)
abstract class TrackDatabase : RoomDatabase() {

    abstract fun continuousTrackDao(): ContinuousTrackDao

    abstract fun todayTrackDisplayDao(): TodayTrackDisplayDao

    abstract fun todaySessionDao(): TodaySessionDao

    abstract fun syncOutboxDao(): SyncOutboxDao

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
                )
                    .fallbackToDestructiveMigration(false)
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
