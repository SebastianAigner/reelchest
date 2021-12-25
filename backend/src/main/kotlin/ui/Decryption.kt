package io.sebi.ui

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.sebi.urldecoder.UrlDecoder

fun Route.decryptEndpointRoute(urlDecoder: UrlDecoder) {
    get("/decrypt") {
        val urlToDecrypt = this.context.request.queryParameters["url"]!!
        val link = urlDecoder.decodeUrl(urlToDecrypt)!!.urls.single()

        call.respondRedirect(link)
    }
}