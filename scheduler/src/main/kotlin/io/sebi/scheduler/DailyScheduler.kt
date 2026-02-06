package io.sebi.scheduler

import kotlinx.coroutines.*
import java.time.*

fun CoroutineScope.scheduleDailyAt(
    time: LocalTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    nowProvider: () -> Instant = { Instant.now() },
    onError: (Throwable) -> Unit = {},
    task: suspend () -> Unit,
): Job {
    return launch(dispatcher) {
        var lastRun: Instant? = null
        while (isActive) {
            val now = nowProvider()
            val nextRun = nextOccurrence(now, time, zoneId, lastRun)
            val delayMillis = Duration.between(now, nextRun).toMillis().coerceAtLeast(0)
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            if (!isActive) return@launch
            try {
                task()
            } catch (t: Throwable) {
                onError(t)
            } finally {
                lastRun = nowProvider()
            }
        }
    }
}

internal fun nextOccurrence(
    now: Instant,
    time: LocalTime,
    zoneId: ZoneId,
    lastRun: Instant?,
): Instant {
    val nowZoned = ZonedDateTime.ofInstant(now, zoneId)
    var nextLocal = nowZoned.toLocalDate().atTime(time)
    if (nextLocal.isBefore(nowZoned.toLocalDateTime())) {
        nextLocal = nextLocal.plusDays(1)
    }
    var nextRun = nextLocal.atZone(zoneId).toInstant()
    if (lastRun != null && !nextRun.isAfter(lastRun)) {
        nextRun = nextLocal.plusDays(1).atZone(zoneId).toInstant()
    }
    return nextRun
}
