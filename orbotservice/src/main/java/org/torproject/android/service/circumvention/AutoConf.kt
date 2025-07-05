package org.torproject.android.service.circumvention

import IPtProxy.IPtProxy
import android.content.Context
import java.net.Authenticator
import java.net.PasswordAuthentication

object AutoConf {

    private const val MEEK_PARAMETERS = "url=https://1723079976.rsc.cdn77.org;front=www.phpmyadmin.net"

    /**
     * Tries to automatically configure Pluggable Transports, if the MOAT service decides, that in your country one is needed.
     *
     * @param country: An ISO 3166-1 Alpha 2 country code to force a specific country.
     * 	 If not provided, the MOAT service will deduct a country from your IP address (preferred!)
     * @param cannotConnectWithoutPt: Set to `true`, if you are sure, that a PT configuration *is needed*,
     * 	 even though the MOAT service says, that in your country none is. In that case, a default configuration will be used.
     * @return A transport to use and a list of custom bridges, if any could be evaluated.
     *
     * @throws MoatApi.MoatError or maybe other IO exceptions.
     */
    suspend fun `do`(
        context: Context,
        country: String? = null,
        cannotConnectWithoutPt: Boolean = false
    ): Pair<Transport, List<String>>? {
        var cannotConnectWithoutPt = cannotConnectWithoutPt

        val done = fun(conf: Pair<Transport, List<String>>?): Pair<Transport, List<String>>? {
            Transport.controller.stop(IPtProxy.MeekLite)
            Authenticator.setDefault(null)

            return conf
        }

        Transport.controller.start(IPtProxy.MeekLite, null)

        val authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                return PasswordAuthentication(MEEK_PARAMETERS, "\u0000".toCharArray())
            }
        }

        Authenticator.setDefault(authenticator)

        val api = MoatApi.getInstance(Transport.controller.port(IPtProxy.MeekLite).toInt())

        // First, update built-ins.
        if (BuiltInBridges.isOutdated(context)) {
            val bridges = api.builtin()

            if (!bridges.empty) {
                bridges.store(context)

                BuiltInBridges.invalidate()
                BuiltInBridges.getInstance(context)
            }
        }

        var response = api.settings(MoatApi.SettingsRequest(country))

        val error = response.errors?.firstOrNull()
        if (error != null) {
            if (error.code == 404 /* Needs transport, but not the available ones */
                || error.code == 406 /* no country from IP address */) {
                cannotConnectWithoutPt = true
            } else {
                done(null)
                throw error
            }
        }

        // If there are no settings, that means that the MOAT service considers the
        // country we're in to be safe for use without any transport.
        // But only consider this, if the user isn't sure, that they cannot connect without PT.
        if (response.settings.isNullOrEmpty() && !cannotConnectWithoutPt) {
            return done(Pair(Transport.NONE, emptyList()))
        }

        // Otherwise, use the first advertised setting which is usable.
        var conf = extract(context, response.settings)
        if (conf != null) return done(conf)

        // If we couldn't understand that answer or it was empty, try the default settings.
        response = api.defaults()
        conf = extract(context, response.settings)

        return done(conf)
    }

    /**
     * Extract the correct PT settings from the given settings from the server.
     *
     * *NOTE*: The priority is given by the server's list sorting. We honor that and always use the first one which works!
     *
     * We try to grab everything we can here:
     * - If there are Snowflake bridge lines given, we update the built-in list of Snowflake bridges.
     * - If there are Obfs4 built-in bridge lines given, we update the built-in list of Obfs4 bridges.
     * - If there are custom Obfs4 bridge lines given, we return these too, regardless of the actually selected transport,
     *      so the user can later try these out, too, if the selected transport doesn't work.
     *
     * @param settings: The settings from the Moat server.
     */
    private fun extract(
        context: Context,
        settings: List<MoatApi.Setting>?
    ): Pair<Transport, List<String>>? {
        var transport: Transport? = null
        var customBridges = mutableListOf<String>()

        for (setting in settings ?: emptyList()) {
            if (setting.bridge.type == "snowflake") {
                val bridges = setting.bridge.bridges

                // If there are Snowflake bridge line updates, update our built-in ones!
                // Note: We ignore the source ("bridgedb" or "builtin") here on purpose.
                if (!bridges.isNullOrEmpty()) {
                    BuiltInBridges.getInstance(context)?.snowflake = bridges.map { Bridge(it) }
                }

                if (transport == null) transport = Transport.SNOWFLAKE
            } else if (setting.bridge.type == "obfs4") {
                if (setting.bridge.source == "builtin") {
                    val bridges = setting.bridge.bridges

                    // If there are Obfs4 bridge line updates, update our built-in ones!
                    if (!bridges.isNullOrEmpty()) {
                        BuiltInBridges.getInstance(context)?.obfs4 = bridges.map { Bridge(it) }
                    }

                    if (transport == null) transport = Transport.OBFS4
                } else if (!setting.bridge.bridges.isNullOrEmpty()) {
                    customBridges.addAll(setting.bridge.bridges)

                    if (transport == null) transport = Transport.CUSTOM
                }
            } else if (setting.bridge.type == "webtunnel") {
                if (setting.bridge.source == "builtin") {
                    val bridges = setting.bridge.bridges

                    // If there are Webtunnel bridge line updates, update our built-in ones!
                    if (!bridges.isNullOrEmpty()) {
                        BuiltInBridges.getInstance(context)?.webtunnel = bridges.map { Bridge(it) }
                    }

                    if (transport == null) transport = Transport.WEBTUNNEL
                } else if (!setting.bridge.bridges.isNullOrEmpty()) {
                    customBridges.addAll(setting.bridge.bridges)

                    if (transport == null) transport = Transport.CUSTOM
                }
            }
        }

        if (transport != null) return Pair(transport, customBridges)

        return null
    }
}