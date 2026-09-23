package org.torproject.android.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreferenceProviderTest {
    @Test
    fun providerCallReturnsSuccessfulResult() {
        assertEquals("value", preferenceProviderCall("fallback") { "value" })
    }

    @Test
    fun providerCallFallsBackWhenProviderIsUnavailable() {
        assertEquals(
            "fallback",
            preferenceProviderCall("fallback") {
                throw IllegalArgumentException("Unknown URI")
            }
        )
    }

    @Test
    fun providerCallDoesNotHideUnrelatedRuntimeFailures() {
        assertFailsWith<IllegalStateException> {
            preferenceProviderCall(Unit) {
                throw IllegalStateException("unrelated failure")
            }
        }
    }
}
