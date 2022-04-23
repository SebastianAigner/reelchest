package io.sebi.ui

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.urldecoder.UrlDecoder

fun Route.decryptEndpointRoute(urlDecoder: UrlDecoder) {
    get("/decrypt") {
        val urlToDecrypt = this.context.request.queryParameters["url"]!!
        val link = urlDecoder.decodeUrl(urlToDecrypt)!!.urls.single()

        call.respondRedirect(link)
    }
}