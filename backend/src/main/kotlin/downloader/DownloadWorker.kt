package io.sebi.downloader


import io.ktor.client.plugins.*
import io.ktor.util.logging.*
import io.sebi.network.NetworkManager
import kotlinx.coroutines.CancellationException
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
        logger.info("Starting work on $myTask")
        try {
            var urls = myTask.getDirectDownloadUrls()
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
