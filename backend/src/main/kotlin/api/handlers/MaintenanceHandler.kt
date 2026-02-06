package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.config.AppConfig
import io.sebi.storage.MetadataStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.File

private val maintenanceLogger = LoggerFactory.getLogger("Maintenance")

suspend fun RoutingContext.scanOrphanedMediaHandler(metadataStorage: MetadataStorage) {
    withContext(Dispatchers.IO) {
        val tombstones = metadataStorage.listTombstones()
        var orphansFound = 0
        var orphansDeleted = 0
        var failedDeletions = 0
        var bytesFreed = 0L

        tombstones.forEach { id ->
            val entryDir = File(AppConfig.mediaLibraryPath, id)
            if (!entryDir.exists() || !entryDir.isDirectory) return@forEach

            val candidateFiles = buildList<File> {
                val mediaFile = File(entryDir, "$id.mp4")
                if (mediaFile.exists() && mediaFile.isFile) add(mediaFile)
                entryDir.listFiles()?.filter { it.name.startsWith("thumb") && it.isFile }?.let { addAll(it) }
            }

            if (candidateFiles.isEmpty()) return@forEach
            orphansFound++

            var failedForId = false
            candidateFiles.forEach { file ->
                val size = file.length()
                val deleted = try {
                    file.delete()
                } catch (e: Exception) {
                    maintenanceLogger.warn("Failed to delete orphaned file: ${file.absolutePath}", e)
                    false
                }

                if (deleted) {
                    bytesFreed += size
                } else {
                    failedForId = true
                }
            }

            if (failedForId) {
                failedDeletions++
            } else {
                orphansDeleted++
            }
        }

        call.respond(buildJsonObject {
            put("orphansFound", orphansFound)
            put("orphansDeleted", orphansDeleted)
            put("failedDeletions", failedDeletions)
            put("bytesFreed", bytesFreed)
        })
    }
}
