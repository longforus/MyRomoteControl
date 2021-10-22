package com.longforus.myremotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableSharedFlow

class NetChangeReceiver(private val onChange: MutableSharedFlow<String>) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String = intent?.getAction() ?: return

        when (action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION, LocationManager.PROVIDERS_CHANGED_ACTION -> onChange.tryEmit(action)
        }
    }
}