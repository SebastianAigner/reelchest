package io.sebi.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.rpc.RPCSearchService
import io.sebi.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class RPCSearchServiceProvider() {
    val logger = LoggerFactory.getLogger("RPCSearchServiceProvider")
    val client = HttpClient(CIO) {
        installKrpc()
    }

    var currentService: RPCSearchService? = null
    val serviceCreationMutex = Mutex()
    suspend fun getSearchService(): RPCSearchService {
        if (currentService != null && currentService?.isActive == true) {
            logger.info("Reusing existing search service")
            return currentService!!
        }
        // we need to create a new service
        return serviceCreationMutex.withLock {
            logger.info("Creating new search service")
            // we check a second time to avoid creating multiple new services
            if (currentService != null && currentService?.isActive == true) {
                return currentService!!
            }
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
            currentService = service
            logger.info("Created new search service")
            service
        }
    }
}

@OptIn(ExperimentalTime::class)
fun Route.searcherApi() {
    val logger = LoggerFactory.getLogger("Searcher API")
    val searchServiceProvider = RPCSearchServiceProvider()
    post("search/{provider}") {
        val provider = call.parameters["provider"]!!
        val (term, offset) = call.receive<SearchRequest>()
        val searchService = call.async {
            logger.info("Obtaining search service for \"$provider\"")
            withTimeoutOrNull(60.seconds) {
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
        val searchService = call.async {
            logger.info("Obtaining search service for listing searchers...")
            withTimeoutOrNull(60.seconds) {
                val (service, duration) = measureTimedValue {
                    searchServiceProvider.getSearchService()
                }
                logger.info("Obtained search service in $duration.")
                service
            }
        }
        logger.info("Listing searchers...")
        val (searchProviders, duration) = measureTimedValue { searchService.await()?.getSearchProviders() }
        logger.info("Searchers obtained in $duration.")
        call.respond(searchProviders ?: error("Failed to obtain search service."))
    }
}