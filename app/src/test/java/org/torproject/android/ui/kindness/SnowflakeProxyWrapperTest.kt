package org.torproject.android.ui.kindness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnowflakeProxyWrapperTest {

    // ensures we aren't running orphaned goroutines with race condition
    // see https://github.com/guardianproject/orbot-android/pull/1807
    @Test
    fun aStartThatStillOwnsItsGenerationCommits() {
        assertTrue(
            SnowflakeProxyWrapper.shouldActuallyStartSnowflakeProxyInIPtProxy(
                generationAtLaunch = 3,
                currentGeneration = 3,
                proxyPresent = false
            )
        )
    }

    // see https://github.com/guardianproject/orbot-android/pull/1807
    @Test
    fun aStopThatArrivedFirstWins() {
        assertFalse(
            SnowflakeProxyWrapper.shouldActuallyStartSnowflakeProxyInIPtProxy(
                generationAtLaunch = 3,
                currentGeneration = 4,
                proxyPresent = false
            )
        )
    }

    // see https://github.com/guardianproject/orbot-android/pull/1807
    @Test
    fun aNewerStartWins() {
        assertFalse(
            SnowflakeProxyWrapper.shouldActuallyStartSnowflakeProxyInIPtProxy(
                generationAtLaunch = 3,
                currentGeneration = 5,
                proxyPresent = false
            )
        )
    }

    // see https://github.com/guardianproject/orbot-android/pull/1807
    @Test
    fun neverDoublesUpOnALiveProxy() {
        assertFalse(
            SnowflakeProxyWrapper.shouldActuallyStartSnowflakeProxyInIPtProxy(
                generationAtLaunch = 3,
                currentGeneration = 3,
                proxyPresent = true
            )
        )
    }

    // see https://github.com/guardianproject/orbot-android/pull/1807
    @Test
    fun handleIntegerOverflow() {
        val generationAtLaunch = Int.MAX_VALUE
        var currentGeneration = Int.MAX_VALUE
        assertTrue(
            SnowflakeProxyWrapper.shouldActuallyStartSnowflakeProxyInIPtProxy(
                generationAtLaunch,
                currentGeneration,
                proxyPresent = false
            )
        )
        currentGeneration++
        assertFalse(
            SnowflakeProxyWrapper.shouldActuallyStartSnowflakeProxyInIPtProxy(
                generationAtLaunch,
                currentGeneration,
                proxyPresent = false
            )
        )
    }


    @Test
    fun portsRoundTrip() {
        val ports = listOf(50000, 50001, 50002)
        val encodedPorts = SnowflakeProxyWrapper.encodeUPnPPortsToPrefs(ports)
        assertEquals(
            ports,
            SnowflakeProxyWrapper.decodeUPnPPorts(encodedPorts)
        )
    }

    // related to tracking state of stale UPnP ports
    // see https://github.com/guardianproject/orbot-android/issues/1795
    @Test
    fun emptyRecordDecodesToNothing() {
        assertTrue(SnowflakeProxyWrapper.decodeUPnPPorts("").isEmpty())
    }

    // see https://github.com/guardianproject/orbot-android/issues/1795
    @Test
    fun garbageInTheRecordIsDropped() {
        assertEquals(
            listOf(50000),
            SnowflakeProxyWrapper.decodeUPnPPorts("50000,junk,,-3,70000")
        )
    }

}
