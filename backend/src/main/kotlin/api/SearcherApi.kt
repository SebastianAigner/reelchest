package io.sebi.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds

class RPCSearchServiceProvider() {

    val client = HttpClient(CIO) {
        installRPC()
    }

    suspend fun getSearchService(): RPCSearchService {
        val service = client.rpc {
            url(
                Json
                    .decodeFromString<JsonArray>(File("userConfig/wsSearcher.json").readText())
                    .first().jsonPrimitive.content
            ) // TODO: Allow more than one searcher
            rpcConfig {
                serialization {
                    json()
                }
            }
        }.withService<RPCSearchService>()
        return service
    }
}

fun Route.searcherApi() {
    val logger = LoggerFactory.getLogger("Searcher API")
    val searchServiceProvider = RPCSearchServiceProvider()
    post("search/{provider}") {
        val provider = call.parameters["provider"]!!
        val (term, offset) = call.receive<SearchRequest>()
        val searchService = async {
            logger.info("Obtaining search service...")
            withTimeoutOrNull(10.seconds) {
                searchServiceProvider.getSearchService()
            }
        }
        val service = searchService.await() ?: run {
            logger.error("Failed to obtain search service.")
            return@post call.respond(HttpStatusCode.ServiceUnavailable)
        }
        logger.info("Searching for \"$term\"")
        call.respond(service.search(provider, term, offset).ifEmpty {
            listOf(SearchResult("Empty search result.", ""))
        })
    }

    get("searchers") {
        val searchService = async { withTimeoutOrNull(10.seconds) { searchServiceProvider.getSearchService() } }
        call.respond(searchService.await()?.getSearchProviders() ?: emptyList())
    }
}