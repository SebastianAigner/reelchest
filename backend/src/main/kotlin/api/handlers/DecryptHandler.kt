package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.urldecoder.UrlDecoder

/**
 * Handler for decrypting a URL and redirecting to the decoded URL.
 */
suspend fun RoutingContext.decryptHandler(urlDecoder: UrlDecoder) {
    val urlToDecrypt = call.request.queryParameters["url"]!!
    val link = urlDecoder.decodeUrl(urlToDecrypt)!!.urls.single()

    call.respondRedirect(link)
}