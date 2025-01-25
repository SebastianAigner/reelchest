package io.sebi.heavymutex

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class HeavyMutex(val name: String) {
    private val mutex = Mutex()
    var lockedAt = Clock.System.now()
    suspend fun lock() {
        mutex.lock()
        lockedAt = Clock.System.now()
    }

    fun unlock() {
        val timeHeld = Clock.System.now() - lockedAt
        println("$name lock held for $timeHeld")
        mutex.unlock()
    }

    suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock {
            lockedAt = Clock.System.now()
            try {
                block()
            } finally {
                val timeHeld = Clock.System.now() - lockedAt
                println("$name lock held for $timeHeld")
            }
        }
    }
}