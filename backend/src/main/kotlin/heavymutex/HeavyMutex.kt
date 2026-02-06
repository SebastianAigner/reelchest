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
    @Volatile
    var lockedAt = Clock.System.now()
        private set
    @Volatile
    var lockedLabel: String? = null
        private set
    val isLocked: Boolean
        get() = mutex.isLocked

    suspend fun lock(label: String) {
        mutex.lock()
        setLockMetadata(label)
    }

    fun unlock() {
        logLockDuration()
        clearLockMetadata()
        mutex.unlock()
    }

    suspend fun <T> withLock(label: String, block: suspend () -> T): T {
        return mutex.withLock {
            setLockMetadata(label)
            try {
                block()
            } finally {
                logLockDuration()
                clearLockMetadata()
            }
        }
    }

    private fun setLockMetadata(label: String) {
        lockedAt = Clock.System.now()
        lockedLabel = label
    }

    private fun clearLockMetadata() {
        lockedLabel = null
    }

    private fun logLockDuration() {
        val timeHeld = Clock.System.now() - lockedAt
        val label = lockedLabel
        if (label.isNullOrBlank()) {
            logger.debug("{} lock held for {}", name, timeHeld)
        } else {
            logger.debug("{} lock ({}) held for {}", name, label, timeHeld)
        }
    }
}
