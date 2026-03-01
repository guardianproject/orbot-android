package org.torproject.android.ui.kindness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class PowerConnectionReceiver(private val snowflakeProxyService: SnowflakeProxyService) :
    BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {
        snowflakeProxyService.powerConnectedCallback(intent.action == Intent.ACTION_POWER_CONNECTED)
    }

    companion object {
        fun getChargingStatusOnDemand(context: Context): Boolean {
            val batteryStatus: Intent? =
                IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

}