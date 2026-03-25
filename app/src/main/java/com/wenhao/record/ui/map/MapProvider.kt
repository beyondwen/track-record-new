package com.wenhao.record.ui.map

import android.content.Context
import com.baidu.mapapi.map.MapView

interface MapProvider {
    fun createMapView(context: Context): MapView
    val providerName: String
}

object BaiduMapProvider : MapProvider {
    override fun createMapView(context: Context): MapView = MapView(context)

    override val providerName: String = "baidu"
}
