package org.torproject.android.service.circumvention

import IPtProxy.IPtProxy
import android.content.Context
import java.net.Authenticator
import java.net.PasswordAuthentication

object AutoConf {

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

        val tunnel = MoatTunnel.TOR_PROJECT

        val done = fun(conf: Pair<Transport, List<String>>?): Pair<Transport, List<String>>? {
            tunnel.stop()
            Authenticator.setDefault(null)

            return conf
        }

        tunnel.start()

        val authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(tunnel.config, "\u0000".toCharArray())
            }
        }
        Authenticator.setDefault(authenticator)

        val api = MoatApi.getInstance(context, tunnel)

        // First, try updating built-ins.
        if (BuiltInBridges.isOutdated(context)) {
            val bridges = try {
                api.builtin()
            } catch (_: Throwable) {
                BuiltInBridges()
            }

            if (!bridges.empty) {
                bridges.store(context)

                BuiltInBridges.invalidate()
                BuiltInBridges.getInstance(context)
            }
        }

        var lastException: Throwable? = null

        var response = try {
            api.settings(MoatApi.SettingsRequest(country?.lowercase()))
        } catch (exception: Throwable) {
            lastException = exception
            null
        }

        response?.errors?.firstOrNull()?.let {
            if (it.code == 404 /* Needs transport, but not the available ones */
                || it.code == 406 /* no country from IP address */) {
                cannotConnectWithoutPt = true
            } else {
                lastException = it
            }
        }

        val response2: MoatApi.SettingsResponse?

        // Guardian Project's Moat is behind DNSTT. There is no source IP address available
        // behind a DNSTT tunnel. So, only execute, when the user gave us a country.
        if (!country.isNullOrEmpty()) {
            val tunnel2 = MoatTunnel.GUARDIAN_PROJECT
            tunnel2.start()

            val authenticator2 = object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(tunnel2.config, "\u0000".toCharArray())
                }
            }
            Authenticator.setDefault(authenticator2)

            val api2 = MoatApi.getInstance(context, tunnel2)

            response2 = try {
                api2.settings(MoatApi.SettingsRequest(country.lowercase()))
            } catch (_: Throwable) {
                null // Ignored, GP's MOAT service is still experimental.
            }

            tunnel2.stop()
            Authenticator.setDefault(authenticator)
        } else {
            response2 = null
        }


        // If there are no settings, that means that the MOAT service considers the
        // country we're in to be safe for use without any transport.
        // But only consider this, if the user isn't sure, that they cannot connect without PT.
        if ((response == null || response.settings.isNullOrEmpty()) && (response2 == null || response2.settings.isNullOrEmpty()) && !cannotConnectWithoutPt) {
            done(null)

            if (lastException != null) throw lastException

            return Pair(Transport.NONE, emptyList())
        }

        // Otherwise, use the first advertised setting which is usable.
        var conf = extract(context, response?.settings)
        val conf2 = extract(context, response2?.settings)
        val transport =
            conf2?.first ?: conf?.first // Prefer Guardian Project MOAT's transport setting.
        val customBridges = (conf?.second ?: emptyList()) + (conf2?.second
            ?: emptyList()) // Use all custom bridges.

        // Otherwise, use the first advertised setting which is usable with IPtProxy.
        if (transport != null) {
            return done(Pair(transport, customBridges))
        }

        // If we couldn't understand that answer or it was empty, try the default settings.
        response = try {
            api.defaults()
        } catch (exception: Throwable) {
            done(null)
            throw exception
        }

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
     * - If there are Webtunnel bridge lines given, we update the built-in list of Webtunnel bridges.
     *      (Their source is ignored, like for Snowflake, since Moat hands them out as "bridgedb".)
     * - If there are DNSTT built-in bridge lines given, we update the built-in list of DNSTT bridges.
     * - If there are custom DNSTT bridge lines given, we return these too, regardless of the actually selected transport.
     *
     * @param settings: The settings from the Moat server.
     */
    private fun extract(
        context: Context,
        settings: List<MoatApi.Setting>?
    ): Pair<Transport, List<String>>? {
        var transport: Transport? = null
        val customBridges = mutableListOf<String>()

        for (setting in settings ?: emptyList()) {
            if (setting.bridge.type == IPtProxy.Snowflake) {
                val bridges = setting.bridge.bridges

                // If there are Snowflake bridge line updates, update our built-in ones!
                // Note: We ignore the source ("bridgedb" or "builtin") here on purpose.
                if (!bridges.isNullOrEmpty()) {
                    BuiltInBridges.getInstance(context)?.snowflake = bridges.map { Bridge(it) }
                }

                if (transport == null) transport = Transport.SNOWFLAKE
            } else if (setting.bridge.type == IPtProxy.Obfs4) {
                if (setting.bridge.source == MoatApi.Bridge.SOURCE_BUILTIN) {
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
            } else if (setting.bridge.type == IPtProxy.Webtunnel) {
                val bridges = setting.bridge.bridges

                // If there are Webtunnel bridge line updates, update our built-in ones!
                // Note: We ignore the source ("bridgedb" or "builtin") here on purpose, like for
                // Snowflake, since Moat hands out webtunnel bridges via its bridgedb source.
                if (!bridges.isNullOrEmpty()) {
                    BuiltInBridges.getInstance(context)?.webtunnel = bridges.map { Bridge(it) }
                }

                if (transport == null) transport = Transport.WEBTUNNEL
            } else if (setting.bridge.type == IPtProxy.Dnstt) {
                if (setting.bridge.source == MoatApi.Bridge.SOURCE_BUILTIN) {
                    val bridges = setting.bridge.bridges

                    // If there are DNSTT bridge line updates, update our built-in ones!
                    if (!bridges.isNullOrEmpty()) {
                        BuiltInBridges.getInstance(context)?.dnstt = bridges.map { Bridge(it) }
                        customBridges.addAll(bridges)
                    }

                    if (transport == null) transport = Transport.CUSTOM
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