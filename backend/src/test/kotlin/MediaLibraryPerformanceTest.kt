package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.fail

class MediaLibraryPerformanceTest : MediaLibraryTestBase() {
    @Test(timeout = 60000)
    fun testMediaInformationEndpointPerformance() = withTestServer { server, client, port ->
        // Configure client timeouts
        client.config {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
            }
        }

        // First, get all media library entries
        val response = client.get("http://localhost:$port/api/mediaLibrary")
        val entries = Json.parseToJsonElement(response.bodyAsText()).jsonArray

        // Prepare to collect timing statistics
        val requestTimes = mutableListOf<Long>()

        try {
            coroutineScope {
                // Process entries in batches to prevent overwhelming the server
                val batchSize = 5
                val batches = entries.chunked(batchSize)

                for (batch in batches) {
                    // Launch parallel requests for current batch
                    val jobs = batch.map { entry ->
                        async {
                            val id = entry.jsonObject["id"]?.jsonPrimitive?.content
                                ?: fail("Entry doesn't have an ID")

                            val time = measureTimeMillis {
                                val infoResponse =
                                    client.get("http://localhost:$port/api/mediaLibrary/$id/media-information")
                                if (infoResponse.status != HttpStatusCode.OK && infoResponse.status != HttpStatusCode.NotFound) {
                                    fail("Request failed with status ${infoResponse.status}")
                                }
                            }
                            synchronized(requestTimes) {
                                requestTimes.add(time)
                            }
                        }
                    }

                    // Wait for current batch to complete
                    jobs.awaitAll()

                    // Add a small delay between batches
                    delay(100)
                }
            }

            // Calculate and output statistics
            val sortedTimes = requestTimes.sorted()
            println("[DEBUG_LOG] Performance Statistics:")
            println("[DEBUG_LOG] Total requests: ${requestTimes.size}")
            println("[DEBUG_LOG] Fastest request: ${sortedTimes.first()}ms")
            println("[DEBUG_LOG] Slowest request: ${sortedTimes.last()}ms")
            println("[DEBUG_LOG] Average request time: ${requestTimes.average()}ms")
            println("[DEBUG_LOG] Median request time: ${sortedTimes[sortedTimes.size / 2]}ms")
        } catch (e: Exception) {
            println("[DEBUG_LOG] Test failed with error: ${e.message}")
            throw e
        }
    }

    @Test(timeout = 60000)
    fun testMediaInformationWithConfigEndpoint() = withTestServer { server, client, port ->
        // Configure client timeouts
        client.config {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
            }
        }

        // First, get all media library entries
        val response = client.get("http://localhost:$port/api/mediaLibrary")
        val entries = Json.parseToJsonElement(response.bodyAsText()).jsonArray

        try {
            coroutineScope {
                // Process entries in batches to prevent overwhelming the server
                val batchSize = 5
                val batches = entries.chunked(batchSize)
                val statusTimes = mutableListOf<Long>()
                var statusRequestCount = 0

                // Start status request job
                val statusJob = async {
                    try {
                        while (isActive) {
                            val time = measureTimeMillis {
                                val statusResponse = client.get("http://localhost:$port/api/status")
                                assertEquals(HttpStatusCode.OK, statusResponse.status, "Status request failed")
                            }
                            synchronized(statusTimes) {
                                statusTimes.add(time)
                                statusRequestCount++
                            }

                            // Check if any status request took too long
                            if (time > 100) { // 100ms threshold for status requests
                                println("[DEBUG_LOG] Warning: Status request took ${time}ms")
                            }

                            delay(50) // Small delay between status requests
                        }
                    } catch (e: Exception) {
                        println("[DEBUG_LOG] Status request job failed: ${e.message}")
                        throw e
                    }
                }

                // Process media info requests in batches
                for (batch in batches) {
                    val jobs = batch.map { entry ->
                        async {
                            val id = entry.jsonObject["id"]?.jsonPrimitive?.content
                                ?: fail("Entry doesn't have an ID")

                            val infoResponse =
                                client.get("http://localhost:$port/api/mediaLibrary/$id/media-information")
                            if (infoResponse.status != HttpStatusCode.OK && infoResponse.status != HttpStatusCode.NotFound) {
                                fail("Media info request failed with status ${infoResponse.status}")
                            }
                        }
                    }

                    // Wait for current batch to complete
                    jobs.awaitAll()
                    delay(100) // Small delay between batches
                }

                // Stop status requests and output statistics
                statusJob.cancel()
                println("[DEBUG_LOG] Status Request Statistics:")
                println("[DEBUG_LOG] Total status requests: $statusRequestCount")
                println("[DEBUG_LOG] Average status request time: ${statusTimes.average()}ms")
                println("[DEBUG_LOG] Max status request time: ${statusTimes.maxOrNull()}ms")
            }
        } catch (e: Exception) {
            println("[DEBUG_LOG] Test failed with error: ${e.message}")
            throw e
        }
    }
}
