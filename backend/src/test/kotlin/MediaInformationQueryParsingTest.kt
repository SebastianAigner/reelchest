package io.sebi

import io.sebi.api.handlers.parseLimit
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaInformationQueryParsingTest {
    @Test
    fun parseLimitAllowsValuesAboveOneHundred() {
        assertEquals(250, parseLimit("250"))
    }

    @Test
    fun parseLimitStillRejectsNonPositiveValues() {
        assertEquals(1, parseLimit("0"))
        assertEquals(1, parseLimit("-10"))
        assertEquals(10, parseLimit("abc"))
        assertEquals(10, parseLimit(null))
    }
}
