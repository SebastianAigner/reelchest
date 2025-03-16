package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.api.RPCSearchServiceProvider
import io.sebi.api.dtos.SearchRequest
import io.sebi.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Handler for searching.
 */
@OptIn(ExperimentalTime::class)
suspend fun RoutingContext.searchHandler(searchServiceProvider: RPCSearchServiceProvider) {
    val logger = LoggerFactory.getLogger("Searcher API")
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
        return call.respond(HttpStatusCode.ServiceUnavailable)
    }
    logger.info("Searching for \"$term\"")
    call.respond(service.search(provider, term, offset).ifEmpty {
        listOf(SearchResult("Empty search result.", ""))
    })
}

/**
 * Handler for getting all searchers.
 */
@OptIn(ExperimentalTime::class)
suspend fun RoutingContext.getSearchersHandler(searchServiceProvider: RPCSearchServiceProvider) {
    val logger = LoggerFactory.getLogger("Searcher API")
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
