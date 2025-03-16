package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.downloader.DownloadManager

/**
 * Handler for the status endpoint.
 */
suspend fun RoutingContext.statusHandler(downloadManager: DownloadManager) {
    call.respond(
        downloadManager.workerStatus()
    )
}
