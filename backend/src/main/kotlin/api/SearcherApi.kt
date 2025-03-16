package io.sebi.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.sebi.rpc.RPCSearchService
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

