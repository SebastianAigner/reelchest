package io.sebi.ui

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.downloader.DownloadManager
import io.sebi.downloader.WithOriginUrl
import io.sebi.ui.shared.commonLayout
import kotlinx.html.*

fun Route.progressList(downloadManager: DownloadManager) {

    get("/progress/{id}") {
        val params = call.parameters["id"]!!
        call.respondText(downloadManager.downloadProgressForId(params).toString())
    }

    get("/downloads") {
        call.respondHtml {
            commonLayout("managed links") {
                h3 {
                    +"enqueued"
                }
                dllist(downloadManager.queuedDownloads)
                h3 {
                    +"in progress"
                }
                dllist(downloadManager.downloadsInProgress)
                h3 {
                    +"done"
                }
                dllist(downloadManager.finishedDownloads)
                script(src = "/static/liveStatusBar.js") {

                }
            }
        }
    }
}

fun BODY.dllist(list: Iterable<WithOriginUrl>, finished: Boolean = false) {
    ul {
        list.forEach {
            li {
                +it.originUrl.take(64)
                +" "
                span(classes = "dlmgr-progress") {
                    id = it.originUrl.shaHashed()
                    +"??"
                }
                +" %"
                (it as? CompletedDownloadTask)?.let {
                    a(href = "/video/${it.originUrl.shaHashed()}") {
                        +"stream"
                    }
                }
            }
        }
    }
}