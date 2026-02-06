package io.sebi.scheduler

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DailySchedulerTest {
    @Test
    fun schedulesSameDayWhenBefore() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2024-01-01T02:30:00Z")
        val expected = Instant.parse("2024-01-01T03:00:00Z")

        val next = nextOccurrence(now, LocalTime.of(3, 0), zoneId, lastRun = null)

        assertEquals(expected, next)
    }

    @Test
    fun schedulesNextDayWhenAfter() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2024-01-01T04:00:00Z")
        val expected = Instant.parse("2024-01-02T03:00:00Z")

        val next = nextOccurrence(now, LocalTime.of(3, 0), zoneId, lastRun = null)

        assertEquals(expected, next)
    }

    @Test
    fun schedulesNextDayWhenAlreadyRanToday() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2024-01-01T03:00:00Z")
        val expected = Instant.parse("2024-01-02T03:00:00Z")

        val next = nextOccurrence(now, LocalTime.of(3, 0), zoneId, lastRun = now)

        assertEquals(expected, next)
    }
}
