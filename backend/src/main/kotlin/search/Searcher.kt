package io.sebi.search

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.sebi.api.SearchRequest
import io.sebi.network.NetworkManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

@Serializable
data class SearcherConfiguration(val endpoint: String)

abstract class Searcher {
    abstract suspend fun search(query: String, pagination: Int = 0): List<SearchResult>
    abstract val endpointName: String
}

private val internalClient = HttpClient(Apache) {
    install(JsonFeature) {

    }
}

abstract class SearcherFactory(val networkManager: NetworkManager) {
    companion object {
        fun instantiateSearchers(): List<Searcher> {
            val arr = Json.decodeFromString<JsonArray>(File("userConfig/searchers.json").readText())
            val configurations = arr.map { Json.decodeFromJsonElement<SearcherConfiguration>(it) }

            return configurations.map { configuration ->
                object : Searcher() {
                    override suspend fun search(query: String, pagination: Int): List<SearchResult> {
                        val sr = internalClient.post<List<SearchResult>>(configuration.endpoint) {
                            contentType(ContentType.Application.Json)
                            body = SearchRequest(query, pagination)
                        }
                        return sr
                    }

                    override val endpointName: String
                        get() = configuration.endpoint
                }
            }
        }

        fun byEndpointName(s: String): Searcher {
            return instantiateSearchers().first { it.endpointName == s }
        }
    }
}

@Serializable
data class SearchResult(val title: String, val url: String, val thumbUrl: String? = null)