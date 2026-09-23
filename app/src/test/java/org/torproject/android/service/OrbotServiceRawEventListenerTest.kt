package org.torproject.android.service

import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// related to Bug https://github.com/guardianproject/orbot-android/pull/1404
// related to PR  https://github.com/guardianproject/orbot-android/pull/1809
class OrbotServiceRawEventListenerTest {

    private fun connectionOver(feed: PipedOutputStream) =
        TorControlConnection(PipedInputStream(feed), ByteArrayOutputStream())

    private fun sendNotice(feed: PipedOutputStream, message: String) {
        feed.write("650 NOTICE $message\r\n".toByteArray())
        feed.flush()
    }

    @Test
    fun secondConnectDeliversEachEventOnce() {
        val feed = PipedOutputStream()
        val conn = connectionOver(feed)

        val delivered = Collections.synchronizedList(mutableListOf<String>())
        val sawMarker = CountDownLatch(1)
        val record = RawEventListener { keyword, data ->
            if (keyword == "NOTICE") {
                delivered.add(data)
                if (data == "marker") sawMarker.countDown()
            }
        }
        val first = RawEventListener { keyword, data -> record.onEvent(keyword, data) }
        val second = RawEventListener { keyword, data -> record.onEvent(keyword, data) }

        OrbotService.replaceRawEventListener(conn, null, first)
        OrbotService.replaceRawEventListener(conn, first, second)
        conn.launchThread(true)

        sendNotice(feed, "Bootstrapped 45%")
        sendNotice(feed, "marker")

        assertTrue(sawMarker.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("Bootstrapped 45%", "marker"), delivered)
    }

    @Test
    fun repeatedReconnectsKeepASingleListener() {
        val feed = PipedOutputStream()
        val conn = connectionOver(feed)

        val delivered = Collections.synchronizedList(mutableListOf<String>())
        val sawMarker = CountDownLatch(1)
        var previous: RawEventListener? = null
        repeat(4) {
            val listener = RawEventListener { keyword, data ->
                if (keyword == "NOTICE") {
                    delivered.add(data)
                    if (data == "marker") sawMarker.countDown()
                }
            }
            OrbotService.replaceRawEventListener(conn, previous, listener)
            previous = listener
        }
        conn.launchThread(true)

        sendNotice(feed, "marker")

        assertTrue(sawMarker.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("marker"), delivered)
    }
}
