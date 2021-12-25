package io.sebi.shutdown

import io.sebi.downloader.DownloadManager
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun setupShutdownHooks(downloadManager: DownloadManager) {
    val logger = LoggerFactory.getLogger("Shutdown Hooks")
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Beginning custom shutdown routine.")
        System.out.flush()
        runBlocking {
            downloadManager.shutdown()
        }
    })
}