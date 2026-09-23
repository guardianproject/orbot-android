package org.torproject.android.ui.more

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogBottomSheetTest {

    @Test
    fun growingLogAppendsOnlyTheNewTail() {
        val displayed = "\nBootstrapped 10%"
        val stored = "\nBootstrapped 10%\nBootstrapped 45%"

        assertEquals("\nBootstrapped 45%", LogBottomSheet.appendableDelta(displayed, stored))
    }

    @Test
    fun coalescedGrowthAppendsAllMissedLines() {
        val displayed = "\nBootstrapped 10%"
        val stored = "\nBootstrapped 10%\nBootstrapped 45%\nBootstrapped 100%"

        assertEquals(
            "\nBootstrapped 45%\nBootstrapped 100%",
            LogBottomSheet.appendableDelta(displayed, stored)
        )
    }

    @Test
    fun clearedAndRegrownLogIsNotSplicedIntoStaleText() {
        val displayed = "\nold session line one\nold session line two"
        val stored = "\nnew session first line\nnew session second line\nnew session third line"

        assertNull(LogBottomSheet.appendableDelta(displayed, stored))
    }

    @Test
    fun shorterStoredLogReplacesDisplayedText() {
        assertNull(LogBottomSheet.appendableDelta("\nline one\nline two", "\nline one"))
    }

    @Test
    fun unchangedLogAppendsNothing() {
        assertNull(LogBottomSheet.appendableDelta("\nline one", "\nline one"))
    }
}
