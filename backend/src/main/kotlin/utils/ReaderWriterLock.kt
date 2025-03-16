package io.sebi.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A utility class that implements a reader-writer lock pattern.
 * This allows multiple readers to access a resource concurrently,
 * but ensures exclusive access for writers.
 */
class ReaderWriterLock {
    private val mutex = Mutex()
    private val roomEmpty = Mutex()
    private var readers = 0

    /**
     * Executes the given critical section with a reader lock.
     * Multiple readers can execute concurrently, but writers will be blocked.
     *
     * @param criticalSection The code to execute with the reader lock
     * @return The result of the critical section
     */
    suspend fun <T> withReaderLock(criticalSection: () -> T): T {
        mutex.withLock {
            readers++
            if (readers == 1)
                roomEmpty.lock()
        }
        try {
            return criticalSection()
        } finally {
            mutex.withLock {
                readers--
                if (readers == 0) {
                    roomEmpty.unlock()
                }
            }
        }
    }

    /**
     * Executes the given critical section with a writer lock.
     * This provides exclusive access to the resource.
     *
     * @param criticalSection The code to execute with the writer lock
     * @return The result of the critical section
     */
    suspend fun <T> withWriterLock(criticalSection: () -> T): T {
        return roomEmpty.withLock {
            criticalSection()
        }
    }
}
