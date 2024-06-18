package io.sebi.network

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory

interface RequestTokenProvider {
    suspend fun takeToken()
}

// TODO: It's used as a singleton. Singletons are meh.
object GlobalRequestTokenProvider : RequestTokenProvider {
    val logger = LoggerFactory.getLogger("Request Token Provider")
    val requestTokenChannel = Channel<Unit>()

    init {
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch {
            logger.info("Global request token provider initialized")
            while (true) {
                logger.info("refilling the bucket!")
                delay(5000)
                requestTokenChannel.send(Unit)
            }
        }
    }

    override suspend fun takeToken() {
        requestTokenChannel.receive()
    }
}