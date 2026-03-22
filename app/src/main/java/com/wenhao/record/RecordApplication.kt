package com.wenhao.record

import android.app.Application
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer

class RecordApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SDKInitializer.setAgreePrivacy(this, true)
        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(CoordType.GCJ02)
    }
}
