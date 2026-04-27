package com.wenhao.record.runtimeusage

import android.content.Context

object RuntimeUsageRecorder {
    fun init(context: Context) = Unit

    fun hit(module: RuntimeUsageModule, detail: String? = null) = Unit
}
