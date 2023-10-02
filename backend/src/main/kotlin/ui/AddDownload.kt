package io.sebi.ui

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.sebi.downloader.IntoMediaLibraryDownloader
import io.sebi.ui.shared.commonLayout
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.form
import kotlinx.html.input

fun Route.addDownload(
    intoMediaLibraryDownloader: IntoMediaLibraryDownloader,
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
        val url = call.request.queryParameters["url"]!!
        intoMediaLibraryDownloader.download(url)
        //downloadUrls.add(Download(text))
        call.respondRedirect("/downloads")
    }
}
