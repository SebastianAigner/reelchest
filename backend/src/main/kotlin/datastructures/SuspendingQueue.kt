package io.sebi.datastructures

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import java.util.concurrent.ConcurrentLinkedQueue

class SuspendingQueue<T> private constructor(private val queue: ConcurrentLinkedQueue<T>) : Iterable<T> {
    constructor() : this(ConcurrentLinkedQueue())

    private val notificationChannel = Channel<Unit>(Channel.UNLIMITED)

    fun add(e: T) {
        queue += e
        notificationChannel.trySendBlocking(Unit)
    }

    suspend fun remove(): T {
        while (true) {
            notificationChannel.receive()
            val item = queue.poll()
            if (item != null) {
                return item
            }
        }
    }

    fun clear() {
        queue.clear()
        while (notificationChannel.tryReceive().isSuccess) {
            // Drain any pending notifications.
        }
    }

    override fun iterator(): Iterator<T> {
        return queue.iterator()
    }
}
