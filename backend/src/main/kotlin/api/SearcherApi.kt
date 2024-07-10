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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.rpc.client.withService
import kotlinx.rpc.serialization.json
import kotlinx.rpc.transport.ktor.client.installRPC
import kotlinx.rpc.transport.ktor.client.rpc
import kotlinx.rpc.transport.ktor.client.rpcConfig

fun Route.searcherApi() {
    val searchService = runBlocking { // TODO: ew!
        withTimeoutOrNull(5000) { // TODO: is this the most idiomatic way of working with the absence of the service?
            // ...mainly because we will also have to treat every single call on the service as "might crash, might timeout". Better way?
            HttpClient(CIO) { installRPC() }.rpc {
                url("ws://localhost:9091/rpc")

                rpcConfig {
                    serialization {
                        json()
                    }
                }
            }.withService<RPCSearchService>()
        }
    }
    val providers = runBlocking(Dispatchers.IO) { // TODO: ew, don't runblocking this.
        withTimeoutOrNull(10000) {
            searchService?.getSearchProviders()
        }
    } ?: emptyList()
    route("search") {
        for (provider in providers) {
            post(provider) {
                val (term, offset) = call.receive<SearchRequest>()
                call.respond(searchService!!.search(provider, term, offset).ifEmpty {
                    listOf(SearchResult("Empty search result.", ""))
                })
            }
        }
    }
    get("searchers") {
        call.respond(searchService?.getSearchProviders() ?: emptyList())
    }
}