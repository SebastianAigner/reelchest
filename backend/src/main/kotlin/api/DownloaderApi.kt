package io.sebi.api

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.downloader.*
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.makeDownloadTask
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

fun Route.downloaderApi(
    downloadManager: DownloadManager,
    urlDecoder: UrlDecoder,
    onCompleteDownload: suspend (CompletedDownloadTask) -> Unit,
) {
    val logger = LoggerFactory.getLogger("Downloader API Routes")
    post("download") {
        val text = call.receive<UrlRequest>()
        println(text)
        downloadManager.enqueueTask(
            urlDecoder.makeDownloadTask(originUrl = text.url, onCompleteDownload)
        )
        call.respondText("OK", status = HttpStatusCode.OK)
    }
    get("queue") {
        val tasks = downloadManager.getDownloads(
            DownloadManager.DownloadType.QUEUED, DownloadManager.DownloadType.CURRENT
        )

        val res = tasks.map {
            MetadatedDownloadQueueEntry(queueEntry = DownloadTaskDTO.from(it), title = it.originUrl)
        }

        call.respond(res)
    }
    route("problematic") {
        get {
            val res = (downloadManager.problematicDownloads).map { dltask ->
                ProblematicTaskDTO.from(dltask)
            }
            call.respond(res)
        }
        post("remove") {
            val text = call.receive<UrlRequest>().url
            downloadManager.problematicDownloads.removeAll { it.originUrl == text }
            call.respond(HttpStatusCode.OK)
        }
        post("retry") {
            val text = call.receive<UrlRequest>().url
            downloadManager.problematicDownloads.removeAll { it.originUrl == text }
            downloadManager.enqueueTask(urlDecoder.makeDownloadTask(text, onCompleteDownload))
            call.respond(HttpStatusCode.OK)
        }
    }
}