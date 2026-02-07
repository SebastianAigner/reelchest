package io.sebi.downloader


import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.sebi.network.NetworkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class DownloadWorker(
    val networkManager: NetworkManager,
    val provideDownload: suspend () -> DownloadTask,
    val id: Int = Random.nextInt(),
    val onComplete: suspend (CompletedDownloadTask) -> Unit,
    val onError: (DownloadTask, Exception) -> Unit,
) {
    val logger = LoggerFactory.getLogger("Download Worker $id")
    var currentTask: DownloadTask? = null // currentTask is for introspecting the worker.
        private set
    val status = AtomicReference<String>("created")

    suspend fun run() {
        while (true) {
            status.set("awaiting")
            logger.info("Awaiting tasks")
            try {
                status.set("providing download")
                val download = provideDownload()
                currentTask = download
                status.set("downloading single item")
                downloadSingleItem(download)
                status.set("downloaded single item")
            } catch (jc: CancellationException) {
                status.set("cancelled")
                logger.error("Cancellation! $jc ${jc.localizedMessage}")
                currentTask?.let {
                    onError(it, jc)
                }
                throw jc
            } catch (e: Exception) {
                status.set("exception")
                logger.error("Firing onError and hoping for the best. Reason:")
                logger.error(e)
                currentTask?.let {
                    onError(it, e)
                    currentTask = null
                }
            }
        }
    }

    private suspend fun downloadSingleItem(myTask: DownloadTask) {
        withContext(Dispatchers.IO) {
            logger.info("Starting work on $myTask")
            try {
                var urls = myTask.getDirectDownloadUrls()
                val streamUrl =
                    if (myTask.onStreamProcess != null && myTask.streamProcessingEligible?.invoke() == true) {
                        val directCandidate = myTask.directStreamUrlProvider?.invoke()
                        val verifiedDirect = directCandidate?.takeIf { isPlaylistManifest(it) }
                        verifiedDirect ?: probeManifestUrl(urls)
                    } else {
                        null
                    }
                if (streamUrl != null && myTask.onStreamProcess != null) {
                    logger.info("Detected manifest for ${myTask.originUrl}, delegating to ffmpeg. Manifest URL: $streamUrl")
                    myTask.progress = 0.05
                    logger.info("Stream stage: manifest detected (5%).")
                    myTask.progress = 0.1
                    logger.info("Stream stage: ffmpeg download starting (10%).")
                    val postProcessing = myTask.onStreamProcess.invoke(streamUrl)
                    myTask.progress = 0.9
                    logger.info("Stream stage: ffmpeg download completed (90%).")
                    val completed = CompletedDownloadTask(
                        targetFile = postProcessing, originUrl = myTask.originUrl
                    )
                    myTask.progress = 0.98
                    logger.info("Stream stage: persisting media library entry (98%).")
                    myTask.onComplete(completed)
                    onComplete(completed)
                    myTask.progress = 1.0
                    logger.info("Stream stage: complete (100%).")
                    currentTask = null
                    return@withContext
                }
                logger.info("No manifest detected for ${myTask.originUrl}; downloading ${urls.size} fragments.")
                val results = mutableListOf<File>()
                for (idx in urls.indices) {
                    try {
                        val fragment = urls[idx]
                        val targetFile = File
                            .createTempFile("vid", ".mp4", File("downloads").apply { mkdir(); })
                            .apply { deleteOnExit() }
                        Downloader(networkManager).download(
                            fragment,
                            targetFile,
                            absoluteProgressCallback = {
                                val progress = it.first.toDouble() / (it.second ?: Long.MAX_VALUE)
                                myTask.progress = if (urls.size > 1) idx.toDouble() / urls.size else progress
                            }
                        )
                        results.add(targetFile)
                    } catch (nfe: HttpNotFoundException) {
                        logger.error("404: Couldn't find ${urls[idx]}")
                        onError(myTask, nfe)
                        currentTask = null
                        break
                    } catch (c: ClientRequestException) {
                        // probably a 403
                        logger.error(c)
                        urls = myTask.getDirectDownloadUrls()
                        throw c
                    }
                }

                logger.info("Completed work on $myTask")
                val postProcessing = myTask.onPostProcess(results)
                val completed = CompletedDownloadTask(
                    targetFile = postProcessing, originUrl = myTask.originUrl
                )
                myTask.onComplete(completed)
                onComplete(
                    completed
                )
                currentTask = null
            } catch (e: IOException) {
                onError(myTask, e)
                currentTask = null
            }
        }
    }

    private fun looksLikeManifestUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("m3u8") ||
                lower.contains("mpd") ||
                lower.contains("manifest") ||
                lower.contains("playlist") ||
                lower.contains("master")
    }

    private suspend fun probeManifestUrl(urls: List<String>): String? {
        if (urls.isEmpty()) return null
        val (likely, rest) = urls.partition { looksLikeManifestUrl(it) }
        val ordered = (likely + rest)
        val maxProbe = minOf(ordered.size, 30)
        logger.info("Probing up to $maxProbe URLs for manifest detection.")
        for (candidate in ordered.take(maxProbe)) {
            if (isPlaylistManifest(candidate)) return candidate
        }
        return null
    }

    private suspend fun isPlaylistManifest(url: String): Boolean {
        return networkManager.getRawClient(noReallyItsOkay = true).prepareGet(url).execute { response ->
            val contentType = response.headers["Content-Type"]?.lowercase()
            if (contentType?.contains("mpegurl") == true ||
                contentType?.contains("m3u8") == true ||
                contentType?.contains("dash+xml") == true
            ) {
                return@execute true
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(1024)
            val headerBuilder = StringBuilder()
            var totalRead = 0
            while (totalRead < 8192 && !channel.isClosedForRead) {
                val bytesToRead = minOf(buffer.size, 8192 - totalRead)
                val bytesRead = channel.readAvailable(buffer, 0, bytesToRead)
                if (bytesRead <= 0) break
                totalRead += bytesRead
                headerBuilder.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
            }
            if (headerBuilder.isEmpty()) return@execute false
            val header = headerBuilder.toString().trimStart()
            header.startsWith("#EXTM3U") ||
                    header.contains("<MPD", ignoreCase = true) ||
                    header.contains("type=\"static\"", ignoreCase = true)
        }
    }
}
