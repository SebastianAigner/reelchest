package io.sebi.heavymutex

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HeavyMutex(
    val name: String,
    private val logger: Logger = LoggerFactory.getLogger(HeavyMutex::class.java),
) {
    private val mutex = Mutex()
    var lockedAt = Clock.System.now()
    suspend fun lock() {
        mutex.lock()
        lockedAt = Clock.System.now()
    }

    fun unlock() {
        val timeHeld = Clock.System.now() - lockedAt
        logger.debug("{} lock held for {}", name, timeHeld)
        mutex.unlock()
    }

    suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock {
            lockedAt = Clock.System.now()
            try {
                block()
            } finally {
                val timeHeld = Clock.System.now() - lockedAt
                logger.debug("{} lock held for {}", name, timeHeld)
            }
        }
    }
}
