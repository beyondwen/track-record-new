package com.wenhao.record

import android.app.Application
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.DecisionEventStorage
import com.wenhao.record.stability.CrashLogStore

class RecordApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
        AutoTrackStorage.warmUp(this)
        HistoryStorage.warmUp(this)
        DecisionEventStorage.warmUp(this)
    }
}
