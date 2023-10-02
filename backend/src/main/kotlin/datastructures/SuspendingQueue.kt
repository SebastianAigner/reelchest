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
        notificationChannel.receive()
        return queue.remove()
    }

    override fun iterator(): Iterator<T> {
        return queue.iterator()
    }
}