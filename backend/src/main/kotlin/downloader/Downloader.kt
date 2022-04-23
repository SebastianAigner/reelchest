package io.sebi.downloader

import io.ktor.client.call.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.sebi.network.NetworkManager
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File


class Downloader(val networkManager: NetworkManager) {
    val logger = LoggerFactory.getLogger("Downloader")
    suspend fun download(
        url: String,
        file: File = File("temp.jpg"),
        absoluteProgressCallback: ((Pair<Long, Long?>) -> Unit)? = null,
        progressCallback: (Double) -> Unit,
    ) = coroutineScope {
        networkManager.getRawClient(noReallyItsOkay = true).prepareGet(url).execute {
            val chan = it.body<ByteReadChannel>()
            var ctr = 0L
            val updateRoutine = launch {
                while (true) {
                    progressCallback(
                        ctr.toDouble() / (it.contentLength() ?: error("could not determine content length!"))
                    )
                    absoluteProgressCallback?.invoke(
                        Pair(ctr, it.contentLength())
                    )
                    delay(2_000)
                }
            }
            while (!chan.isClosedForRead) {
                yield()
                chan.read { buf ->
                    val ba = ByteArray(buf.remaining())
                    buf.get(ba)
                    file.appendBytes(ba)
                    ctr += ba.size
                }
            }

            logger.info("Concluded work for URL $url.")
            progressCallback(1.0)
            absoluteProgressCallback?.invoke(Pair(ctr, it.contentLength()))
            updateRoutine.cancelAndJoin()
        }
    }
}
