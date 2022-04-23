package io.sebi.ui

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.downloader.DownloadManager
import io.sebi.ui.shared.commonLayout
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.makeDownloadTask
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.form
import kotlinx.html.input

fun Route.addDownload(
    downloadManager: DownloadManager,
    urlDecoder: UrlDecoder,
    onCompleteDownload: suspend (CompletedDownloadTask) -> Unit,
) {
    get("/add") {
        call.respondHtml {
            commonLayout("add link") {
                form(action = "/dl", method = FormMethod.get) {
                    input(type = InputType.text, name = "url") {
                        this.placeholder = "url"
                    }
                    input(type = InputType.submit)
                }
            }
        }
    }

    get("/dl") {
        val text = call.request.queryParameters["url"]!!
        println(text)
        downloadManager.enqueueTask(
            urlDecoder.makeDownloadTask(text, onCompleteDownload)
        )
        //downloadUrls.add(Download(text))
        call.respondRedirect("/downloads")
    }
}
