package org.torproject.android.service.circumvention

import IPtProxy.IPtProxy
import android.content.Context
import org.torproject.android.service.OrbotService
import java.net.Authenticator
import java.net.PasswordAuthentication

object Moat {

    /**
     * Initializes MeekLite using IPtProxy, and configures HTTP authentication
     * based off of the moat-url and moat-front assets.
     * @return the port MeekLite is running on
     */
    fun startMeekForMoatApi(context: Context): Long {

        val iPtProxy = OrbotService.getIptProxyController(context)
        iPtProxy.start(IPtProxy.MeekLite, null)

        val moatUrl = OrbotService.getCdnFront("moat-url")
        val front = OrbotService.getCdnFront("moat-front")

        val pUsername = "url=$moatUrl;front=$front"
        val pPassword = "\u0000"

        val authenticator: Authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(pUsername, pPassword.toCharArray())
            }
        }

        Authenticator.setDefault(authenticator)
        return iPtProxy.port(IPtProxy.MeekLite)
    }

    /**
     * Stops MeekLite and removes the HTTP Authentication that was configured in
     * {@link #startMeekForMoatApi}
     */
    fun stopMeekForMoatApi(context: Context) {
        Authenticator.setDefault(null)
        val iPtProxy = OrbotService.getIptProxyController(context)
        iPtProxy.stop(IPtProxy.MeekLite)
    }

}