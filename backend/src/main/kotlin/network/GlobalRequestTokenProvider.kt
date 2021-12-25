package io.sebi.network

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

// TODO: It's a singleton. Singletons are meh.
object GlobalRequestTokenProvider {
    val requestTokenChannel = Channel<Unit>()

    init {
        println("Global request token provider initialized")
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch {
            while (true) {
                println("refilling the bucket!")
                delay(5000)
                requestTokenChannel.send(Unit)
            }
        }
        println("Launched!")
    }
}