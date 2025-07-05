package org.torproject.android.service.circumvention

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.android.service.util.Prefs
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

object SmartConnect {

    private val ioScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    private val mainScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Main)
    }

    private var progress = 0
    private var connectionTimeout = TimeSource.Monotonic.markNow()
    private var connectionGuard: Timer? = null

    @JvmStatic
    fun handle(context: Context, startTor: () -> Exception?, stopTor: (e: Exception?) -> Unit, completed: () -> Unit) {
        progress = 0

        if (!Prefs.smartConnect) {
            val exception = startTor()
            return if (exception != null) stopTor(exception) else completed()
        }

        ioScope.launch {
            var conf: Pair<Transport, List<String>>? = null

            try {
                conf = AutoConf.`do`(context)
            }
            catch (_: Throwable) {}

            Prefs.transport = conf?.first ?: Transport.NONE

            conf?.second?.let {
                if (it.isNotEmpty()) {
                    Prefs.bridgesList = it.joinToString("\n")
                }
            }

            Prefs.transport.start(context)
            val exception = startTor()

            if (exception != null) {
                mainScope.launch {
                    stopTor(exception)
                }

                return@launch
            }

            // Assume everything's fine for the next 30 seconds.
            connectionAlive()

            connectionGuard = Timer()
            connectionGuard?.schedule(object : TimerTask() {
                override fun run() {
                    // Since we seem to have a working connection now, disable smart connect.
                    if (progress >= 100) {
                        stopConnectionGuard()
                        Prefs.smartConnect = false

                        mainScope.launch {
                            completed()
                        }

                        return
                    }

                    if (TimeSource.Monotonic.markNow() < connectionTimeout) return

                    connectionAlive()

                    var connected = false

                    mainScope.launch {
                        stopTor(null)
                    }

                    do {
                        when (Prefs.transport) {
                            Transport.NONE -> {
                                Prefs.transport = Transport.SNOWFLAKE

                                try {
                                    Prefs.transport.start(context)
                                    connected = true
                                }
                                catch(_: Exception) {}
                            }
                            Transport.SNOWFLAKE, Transport.SNOWFLAKE_AMP,Transport.SNOWFLAKE_SQS -> {
                                Prefs.transport.stop()

                                Prefs.transport = if (!Prefs.bridgesList.isNullOrEmpty()) {
                                    Transport.CUSTOM
                                }
                                else {
                                    Transport.OBFS4
                                }

                                try {
                                    Prefs.transport.start(context)
                                    connected = true
                                }
                                catch(_: Exception) {}
                            }
                            Transport.CUSTOM -> {
                                Prefs.transport.stop()

                                Prefs.transport = Transport.OBFS4

                                try {
                                    Prefs.transport.start(context)
                                    connected = true
                                }
                                catch (_: Exception) {}
                            }
                            else -> {
                                stopConnectionGuard()

                                Prefs.transport.stop()

                                mainScope.launch {
                                    stopTor(Exception("Smart Connect failed"))
                                }

                                return
                            }
                        }
                    } while (!connected)

                    val exception = startTor()
                    if (exception != null) {
                        // That didn't start? Ok, then try next transport immediately.
                        connectionTimeout = TimeSource.Monotonic.markNow()
                    }
                }
            }, 1000, 1000)
        }
    }

    @JvmStatic
    fun updateProgress(progress: Int) {
        if (!Prefs.smartConnect) return

        if (progress > this.progress) {
            connectionAlive()
            this.progress = progress
        }
    }

    private fun connectionAlive() {
        connectionTimeout = TimeSource.Monotonic.markNow() + Prefs.smartConnectTimeout.seconds
    }

    private fun stopConnectionGuard() {
        connectionGuard?.cancel()
        connectionGuard = null
    }
}