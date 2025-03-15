package io.sebi.helpertools

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Helper tool to download all media library hashes from a remote reelchest instance
 * and save them in .bin format.
 * Generated code, no real decisions were made here.
 */
object HashDownloader {
    private val logger = LoggerFactory.getLogger(HashDownloader::class.java)
    private const val REMOTE_URL = ""
    private const val OUTPUT_DIR = "all-remote-hashes"

    // Create our own Ktor client as per the issue description
    private val client = HttpClient(CIO) {
        install(UserAgent) {
            agent = "ReelChest Hash Downloader Tool"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 60000
        }
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        logger.info("Starting hash download from $REMOTE_URL")

        // Create output directory if it doesn't exist
        val outputDir = File(OUTPUT_DIR)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
            logger.info("Created output directory: ${outputDir.absolutePath}")
        }

        // Get all media entries from the remote instance
        val mediaEntries = getMediaEntries()
        logger.info("Found ${mediaEntries.size} media entries")

        // Download hashes for each entry
        var successCount = 0
        var failCount = 0

        for (entry in mediaEntries) {
            try {
                val id = entry.jsonObject["id"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Media entry has no ID")

                // Check if hash file already exists
                val outputFile = File(outputDir, "$id.bin")
                if (outputFile.exists()) {
                    logger.info("Hash file already exists for media ID: $id, skipping download")
                    successCount++
                    continue
                }

                logger.info("Downloading hashes for media ID: $id")
                val hashBytes = downloadHashBinary(id)

                if (hashBytes != null) {
                    // Save to file named after the ID
                    outputFile.writeBytes(hashBytes)
                    logger.info("Saved hashes to ${outputFile.absolutePath}")
                    successCount++
                } else {
                    logger.warn("No hashes found for media ID: $id")
                    failCount++
                }
            } catch (e: Exception) {
                logger.error("Error downloading hashes for entry: ${entry.jsonObject["id"]?.jsonPrimitive?.content}", e)
                failCount++
            }
        }

        logger.info("Hash download complete. Success: $successCount, Failed: $failCount")
    }

    /**
     * Get all media entries from the remote instance
     */
    private suspend fun getMediaEntries(): List<JsonElement> {
        val response = client.get("$REMOTE_URL/api/mediaLibrary")
        val jsonText = response.bodyAsText()
        return Json.parseToJsonElement(jsonText).jsonArray
    }

    /**
     * Download hash in binary format for a specific media ID
     */
    private suspend fun downloadHashBinary(id: String): ByteArray? {
        return try {
            val response = client.get("$REMOTE_URL/api/mediaLibrary/$id/hash.bin")
            if (response.status.value == 200) {
                response.body()
            } else {
                logger.warn("Failed to download hash for ID $id: ${response.status}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error downloading hash for ID $id", e)
            null
        }
    }
}
