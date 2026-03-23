package com.wenhao.record

import android.app.Application
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.stability.CrashLogStore

class RecordApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
        SDKInitializer.setAgreePrivacy(this, true)
        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(CoordType.GCJ02)
        AutoTrackStorage.warmUp(this)
        HistoryStorage.warmUp(this)
    }
}
