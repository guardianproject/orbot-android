package org.torproject.android.service.circumvention

import IPtProxy.Controller
import IPtProxy.IPtProxy
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

object LyrebirdClient {
    @JvmStatic
    fun startWithMeekLite(iPtProxy: Controller) {
        try {
            iPtProxy.start(IPtProxy.MeekLite, "")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun stop(iPtProxy: Controller) {
        iPtProxy.stop(IPtProxy.MeekLite)
    }

    @JvmStatic
    fun getClientTransportPluginTorrcLine(iPtProxy: Controller) : String{
        return "ClientTransportPlugin meek_lite socks5 127.0.0.1:${iPtProxy.port(IPtProxy.MeekLite)}\n"
    }

    @JvmStatic
    fun getLocalBrokersMeek(context: Context) : List<String>{
        val brokers = ArrayList<String>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("meek-brokers")))
            reader.forEachLine { brokers.add(it) }
            reader.close()
        } catch (e: IOException) {
            Log.e("LyrebirdClient", "$e")
        }
        return brokers
    }

}
