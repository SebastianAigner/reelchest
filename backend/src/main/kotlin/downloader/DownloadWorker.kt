package io.sebi.downloader


import io.ktor.client.plugins.*
import io.ktor.util.logging.*
import io.sebi.network.NetworkManager
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
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

    suspend fun run() {
        while (true) {
            logger.info("Awaiting tasks")
            try {
                provideDownload().let {
                    currentTask = it
                    downloadSingleItem(it)
                }
            } catch (jc: CancellationException) {
                logger.info("Cancellation! $jc ${jc.localizedMessage}")
                currentTask?.let {
                    onError(it, jc)
                }
                return
            } catch (e: Exception) {
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
                    val targetFile = File.createTempFile(
                        "vid",
                        ".mp4",
                        File("downloads").apply { mkdir(); }
                    ).apply { deleteOnExit() }
                    Downloader(networkManager).download(fragment, targetFile, absoluteProgressCallback = {
                        //println("[worker #$id]: ${it.first} / ${it.second}")
                    }) { progress ->
                        myTask.progress =
                            if (urls.size > 1) idx.toDouble() / urls.size else progress
                    }
                    results.add(targetFile)
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
                targetFile = postProcessing,
                originUrl = myTask.originUrl
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
