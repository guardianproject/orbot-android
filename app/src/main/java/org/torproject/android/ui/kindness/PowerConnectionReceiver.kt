package org.torproject.android.ui.kindness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerConnectionReceiver(private val snowflakeProxyService: SnowflakeProxyService) :
    BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        snowflakeProxyService.powerConnectedCallback(intent.action == Intent.ACTION_POWER_CONNECTED)
    }
}