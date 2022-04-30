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

        val nameRequests = tasks.map { dltask ->
            val deferredTitle = async {
                try {
                    urlDecoder.getMetadata(dltask.originUrl)?.title
                } catch (e: Throwable) {
                    logger.error(e.stackTraceToString())
                    null
                }
            }
            dltask to deferredTitle
        }

        val tasksToNames = tasks.associateWith {
            it.originUrl
        }.toMutableMap()

        withTimeoutOrNull(10000) {
            val remaining = nameRequests.toMutableList()
            repeat(nameRequests.size) {
                val selected = select<Pair<DownloadTask, String?>> {
                    for (req in remaining) {
                        req.second.onAwait { req.first to it }
                    }
                }
                selected.second?.let {
                    tasksToNames[selected.first] = it
                    remaining.removeIf { it.first == selected.first }
                }
            }
        }

        val res = tasksToNames.map { (task, name) ->
            MetadatedDownloadQueueEntry(queueEntry = DownloadTaskDTO.from(task), title = name)
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