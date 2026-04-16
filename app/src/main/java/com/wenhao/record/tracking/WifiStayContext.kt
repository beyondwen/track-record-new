package com.wenhao.record.tracking

data class WifiSnapshot(
    val isConnected: Boolean,
    val ssid: String? = null,
    val bssid: String? = null
)

class WifiStayContext {
    private var anchorWifiBssid: String? = null

    fun update(snapshot: WifiSnapshot, insideAnchor: Boolean) {
        if (!insideAnchor) return
        val currentBssid = snapshot.bssid ?: return
        if (currentBssid.isNotBlank() && currentBssid != "02:00:00:00:00:00") {
            anchorWifiBssid = currentBssid
        }
    }

    fun isSameAnchorWifi(snapshot: WifiSnapshot, insideAnchor: Boolean): Boolean {
        if (!insideAnchor || !snapshot.isConnected) return false
        val currentBssid = snapshot.bssid ?: return false
        return currentBssid.isNotBlank() && currentBssid == anchorWifiBssid
    }

    fun clear() {
        anchorWifiBssid = null
    }
}
