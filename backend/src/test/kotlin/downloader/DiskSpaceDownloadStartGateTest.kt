package io.sebi.downloader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiskSpaceDownloadStartGateTest {
    @Test
    fun requiresStrictlyMoreThanMinimum() {
        val minBytes = DiskSpaceDownloadStartGate.MIN_FREE_BYTES

        assertFalse(hasEnoughSpace(minBytes, minBytes))
        assertTrue(hasEnoughSpace(minBytes + 1, minBytes))
    }
}
