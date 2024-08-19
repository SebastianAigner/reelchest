package io.sebi.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.rpc.RPCSearchService
import io.sebi.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.rpc.client.withService
import kotlinx.rpc.serialization.json
import kotlinx.rpc.transport.ktor.client.installRPC
import kotlinx.rpc.transport.ktor.client.rpc
import kotlinx.rpc.transport.ktor.client.rpcConfig
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

fun Route.searcherApi() {
    val logger = LoggerFactory.getLogger("Searcher API")
    val searchService by lazy { // cool: lazy ensures this only happens once, even concurrently!
        application.async { // todo: if the service isn't available on first request, searchService will stay null for the duration of the program.
            withTimeoutOrNull(60.seconds) { // TODO: is this the most idiomatic way of working with the absence of the service?
                // ...mainly because we will also have to treat every single call on the service as "might crash, might timeout". Better way?
                HttpClient(CIO) {
                    installRPC()
                }.rpc {
                    url("ws://localhost:9091/rpc")
                    rpcConfig {
                        serialization {
                            json()
                        }
                    }
                }.withService<RPCSearchService>()
            } ?: run {
                logger.warn("Search service discovery timed out.")
                null
            }
        }
    }
    post("search/{provider}") {
        val provider = call.parameters["provider"]!!
        val (term, offset) = call.receive<SearchRequest>()
        call.respond(searchService.await()!!.search(provider, term, offset).ifEmpty {
            listOf(SearchResult("Empty search result.", ""))
        })
    }

    get("searchers") {
        call.respond(searchService.await()?.getSearchProviders() ?: emptyList())
    }
}