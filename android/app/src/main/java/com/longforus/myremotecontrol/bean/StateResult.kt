package com.longforus.myremotecontrol.bean

import java.net.InetAddress

data class StateResult(
    var message: CharSequence? = null,
    var permissionGranted: Boolean = false,
    var locationRequirement: Boolean = false,
    var wifiConnected: Boolean = false,
    var is5G: Boolean = false,
    var address: InetAddress? = null,
    var ssid: String? = null,
    var pwd: String? = null,
    var ssidBytes: ByteArray? = null,
    var bssid: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StateResult

        if (message != other.message) return false
        if (permissionGranted != other.permissionGranted) return false
        if (locationRequirement != other.locationRequirement) return false
        if (wifiConnected != other.wifiConnected) return false
        if (is5G != other.is5G) return false
        if (address != other.address) return false
        if (ssid != other.ssid) return false
        if (ssidBytes != null) {
            if (other.ssidBytes == null) return false
            if (!ssidBytes.contentEquals(other.ssidBytes)) return false
        } else if (other.ssidBytes != null) return false
        if (bssid != other.bssid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message?.hashCode() ?: 0
        result = 31 * result + permissionGranted.hashCode()
        result = 31 * result + locationRequirement.hashCode()
        result = 31 * result + wifiConnected.hashCode()
        result = 31 * result + is5G.hashCode()
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + (ssid?.hashCode() ?: 0)
        result = 31 * result + (ssidBytes?.contentHashCode() ?: 0)
        result = 31 * result + (bssid?.hashCode() ?: 0)
        return result
    }
}