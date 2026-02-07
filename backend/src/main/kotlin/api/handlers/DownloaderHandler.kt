package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.api.dtos.MetadatedDownloadQueueEntry
import io.sebi.api.dtos.UrlRequest
import io.sebi.downloader.*
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.makeDownloadTask

/**
 * Handler for downloading a URL.
 */
suspend fun RoutingContext.downloadUrlHandler(
    downloadManager: DownloadManager,
    urlDecoder: UrlDecoder,
    onCompleteDownload: suspend (CompletedDownloadTask) -> Unit,
) {
    val text = call.receive<UrlRequest>()
    println(text)
    val result = downloadManager.enqueueTask(
        urlDecoder.makeDownloadTask(originUrl = text.url, onCompleteDownload)
    )
    when (result) {
        is DownloadEnqueueResult.Enqueued -> call.respondText("OK", status = HttpStatusCode.OK)
        is DownloadEnqueueResult.Skipped -> call.respond(
            HttpStatusCode.Conflict,
            mapOf("error" to result.reason)
        )
    }
}

/**
 * Handler for getting the download queue.
 */
suspend fun RoutingContext.getDownloadQueueHandler(downloadManager: DownloadManager) {
    val tasks = downloadManager.getDownloads(
        DownloadType.QUEUED, DownloadType.CURRENT
    )

    val res = tasks.map {
        MetadatedDownloadQueueEntry(queueEntry = DownloadTaskDTO.from(it), title = it.originUrl)
    }

    call.respond(res)
}

/**
 * Handler for getting problematic downloads.
 */
suspend fun RoutingContext.getProblematicDownloadsHandler(downloadManager: DownloadManager) {
    val res = (downloadManager.problematicDownloads).map { dltask ->
        ProblematicTaskDTO.from(dltask)
    }
    call.respond(res)
}

/**
 * Handler for removing a problematic download.
 */
suspend fun RoutingContext.removeProblematicDownloadHandler(downloadManager: DownloadManager) {
    val text = call.receive<UrlRequest>().url
    downloadManager.problematicDownloads.removeAll { it.originUrl == text }
    call.respond(HttpStatusCode.OK)
}

/**
 * Handler for retrying a problematic download.
 */
suspend fun RoutingContext.retryProblematicDownloadHandler(
    downloadManager: DownloadManager,
    urlDecoder: UrlDecoder,
    onCompleteDownload: suspend (CompletedDownloadTask) -> Unit,
) {
    val text = call.receive<UrlRequest>().url
    downloadManager.problematicDownloads.removeAll { it.originUrl == text }
    val result = downloadManager.enqueueTask(urlDecoder.makeDownloadTask(text, onCompleteDownload))
    when (result) {
        is DownloadEnqueueResult.Enqueued -> call.respond(HttpStatusCode.OK)
        is DownloadEnqueueResult.Skipped -> call.respond(
            HttpStatusCode.Conflict,
            mapOf("error" to result.reason)
        )
    }
}
