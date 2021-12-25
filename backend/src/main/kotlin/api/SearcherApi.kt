package io.sebi.api

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.sebi.search.SearchResult
import io.sebi.search.SearcherFactory

fun Route.searcherApi() {
    route("search") {
        //initialize route
        for (s in SearcherFactory.instantiateSearchers()) {
            post(s.endpointName) {
                val (term, offset) = call.receive<SearchRequest>()
                call.respond(s.search(term, offset).ifEmpty {
                    listOf(SearchResult("Empty search result.", ""))
                })
            }
        }
    }
    get("searchers") {
        call.respond(SearcherFactory.instantiateSearchers().map { it.endpointName })
    }
}