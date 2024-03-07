package io.sebi.downloader

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.sebi.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream


class Downloader(val networkManager: NetworkManager) {
    val logger = LoggerFactory.getLogger("Downloader")
    suspend fun download(
        url: String,
        file: File = File("temp.jpg"),
        absoluteProgressCallback: ((Pair<Long, Long?>) -> Unit),
    ) = coroutineScope {
        val fileOutputChannel = FileOutputStream(file).channel
        networkManager.getRawClient(noReallyItsOkay = true).prepareGet(url).execute {
            val chan = it.body<ByteReadChannel>()
            var ctr = 0L

            withContext(Dispatchers.IO) {
                while (!chan.isClosedForRead) {
                    yield()
                    // TODO: It will fail with EOFException if not enough bytes (availableForRead < min) available in the channel after it is closed.
                    // TODO: We could alternatively use readAvailable, but that one is entirely synchronous.
                    // we actively set min to 0 (it's 1 by default) to avoid throwing that EOFException. Let's see how it plays out!
                    chan.read(min = 0) { buf ->
                        val rem = buf.remaining()
                        fileOutputChannel.write(buf) // This is a synchronous operation
                        ctr += rem
                        absoluteProgressCallback(ctr to it.contentLength())
                    }
                }
            }

            logger.info("Concluded work for URL $url.")
            absoluteProgressCallback(ctr to it.contentLength())
        }
    }
}
