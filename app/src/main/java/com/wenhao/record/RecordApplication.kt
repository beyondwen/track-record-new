package com.wenhao.record

import android.app.Application
import com.wenhao.record.data.tracking.TrackUploadScheduler
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import com.wenhao.record.runtimeusage.RuntimeUsageModule
import com.wenhao.record.runtimeusage.RuntimeUsageRecorder
import com.wenhao.record.stability.CrashLogStore

class RecordApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RuntimeUsageRecorder.init(this)
        RuntimeUsageRecorder.hit(RuntimeUsageModule.APP_PROCESS)
        CrashLogStore.install(this)
        TrackingRuntimeSnapshotStorage.warmUp(this)
        TrackUploadScheduler.ensureScheduled(this)
    }
}
