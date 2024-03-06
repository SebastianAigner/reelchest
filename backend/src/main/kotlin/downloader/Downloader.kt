package io.sebi.downloader

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.sebi.network.NetworkManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.io.File


class Downloader(val networkManager: NetworkManager) {
    val logger = LoggerFactory.getLogger("Downloader")
    suspend fun download(
        url: String,
        file: File = File("temp.jpg"),
        absoluteProgressCallback: ((Pair<Long, Long?>) -> Unit),
    ) = coroutineScope {
        networkManager.getRawClient(noReallyItsOkay = true).prepareGet(url).execute {
            val chan = it.body<ByteReadChannel>()
            var ctr = 0L

            while (!chan.isClosedForRead) {
                yield()
                chan.read { buf ->
                    val ba = ByteArray(buf.remaining())
                    buf.get(ba)
                    file.appendBytes(ba)
                    ctr += ba.size
                    absoluteProgressCallback(ctr to it.contentLength())
                }
            }

            logger.info("Concluded work for URL $url.")
            absoluteProgressCallback(ctr to it.contentLength())
        }
    }
}
