package io.sebi.rpc

import io.sebi.search.SearchResult
import kotlinx.rpc.RPC

interface RPCSearchService : RPC {
    suspend fun getSearchProviders(): List<String>
    suspend fun search(provider: String, query: String, offset: Int): List<SearchResult>
}