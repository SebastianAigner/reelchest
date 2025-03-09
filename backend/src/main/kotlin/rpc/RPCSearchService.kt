package io.sebi.rpc

import io.sebi.search.SearchResult
import kotlinx.rpc.RemoteService
import kotlinx.rpc.annotations.Rpc

@Rpc
interface RPCSearchService : RemoteService {
    suspend fun getSearchProviders(): List<String>
    suspend fun search(provider: String, query: String, offset: Int): List<SearchResult>
}