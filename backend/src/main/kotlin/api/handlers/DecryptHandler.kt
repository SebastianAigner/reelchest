package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.urldecoder.DecryptServiceException
import io.sebi.urldecoder.ErrorResponse
import io.sebi.urldecoder.UrlDecoder

/**
 * Handler for decrypting a URL and redirecting to the decoded URL.
 */
suspend fun RoutingContext.decryptHandler(urlDecoder: UrlDecoder) {
    val urlToDecrypt = call.request.queryParameters["url"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_URL", "URL is required"))
    val link = try {
        urlDecoder.decodeUrl(urlToDecrypt)?.urls?.single()
            ?: return call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("NO_MATCH", "No decoder could decrypt the URL")
            )
    } catch (e: DecryptServiceException) {
        val status = e.status ?: HttpStatusCode.BadGateway
        return call.respond(status, ErrorResponse(e.errorCode, e.message ?: "Decoder error"))
    }

    call.respondRedirect(link)
}
