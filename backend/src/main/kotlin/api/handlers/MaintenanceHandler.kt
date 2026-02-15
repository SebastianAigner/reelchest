package io.sebi.api.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.config.AppConfig
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.File

private val maintenanceLogger = LoggerFactory.getLogger("Maintenance")

private data class OrphanScanState(
    val status: String,
    val totalTombstones: Int,
    val processed: Int,
    val orphansFound: Int,
    val orphansDeleted: Int,
    val failedDeletions: Int,
    val bytesFreed: Long,
    val startedAt: Long?,
    val finishedAt: Long?,
    val error: String?,
)

private fun idleOrphanScanState() = OrphanScanState(
    status = "idle",
    totalTombstones = 0,
    processed = 0,
    orphansFound = 0,
    orphansDeleted = 0,
    failedDeletions = 0,
    bytesFreed = 0L,
    startedAt = null,
    finishedAt = null,
    error = null
)

private val orphanScanMutex = Mutex()
private val orphanScanStateFlow = MutableStateFlow(idleOrphanScanState())
private var orphanScanJob: Job? = null

private fun buildOrphanScanJson(state: OrphanScanState) = buildJsonObject {
    put("status", state.status)
    put("totalTombstones", state.totalTombstones)
    put("processed", state.processed)
    put("orphansFound", state.orphansFound)
    put("orphansDeleted", state.orphansDeleted)
    put("failedDeletions", state.failedDeletions)
    put("bytesFreed", state.bytesFreed)
    state.startedAt?.let { put("startedAt", it) }
    state.finishedAt?.let { put("finishedAt", it) }
    state.error?.let { put("error", it) }
}

private suspend fun startOrphanScan(metadataStorage: MetadataStorage, application: Application): OrphanScanState {
    return orphanScanMutex.withLock {
        val currentJob = orphanScanJob
        if (currentJob != null && currentJob.isActive) {
            return@withLock orphanScanStateFlow.value
        }

        val tombstones = metadataStorage.listTombstones()
        val startedAt = System.currentTimeMillis()
        var state = OrphanScanState(
            status = "running",
            totalTombstones = tombstones.size,
            processed = 0,
            orphansFound = 0,
            orphansDeleted = 0,
            failedDeletions = 0,
            bytesFreed = 0L,
            startedAt = startedAt,
            finishedAt = null,
            error = null
        )
        orphanScanStateFlow.value = state

        lateinit var job: Job
        job = application.launch(Dispatchers.IO) {
            try {
                tombstones.forEach { id ->
                    val entryDir = File(AppConfig.mediaLibraryPath, id)
                    if (entryDir.exists() && entryDir.isDirectory) {
                        val candidateFiles = buildList<File> {
                            val mediaFile = File(entryDir, "$id.mp4")
                            if (mediaFile.exists() && mediaFile.isFile) add(mediaFile)
                            entryDir.listFiles()?.filter { it.name.startsWith("thumb") && it.isFile }
                                ?.let { addAll(it) }
                        }

                        if (candidateFiles.isNotEmpty()) {
                            var failedForId = false
                            var bytesFreedDelta = 0L
                            candidateFiles.forEach { file ->
                                val size = file.length()
                                val deleted = try {
                                    file.delete()
                                } catch (e: Exception) {
                                    maintenanceLogger.warn(
                                        "Failed to delete orphaned file: ${file.absolutePath}",
                                        e
                                    )
                                    false
                                }

                                if (deleted) {
                                    bytesFreedDelta += size
                                } else {
                                    failedForId = true
                                }
                            }

                            state = state.copy(
                                orphansFound = state.orphansFound + 1,
                                orphansDeleted = state.orphansDeleted + if (failedForId) 0 else 1,
                                failedDeletions = state.failedDeletions + if (failedForId) 1 else 0,
                                bytesFreed = state.bytesFreed + bytesFreedDelta
                            )
                        }
                    }

                    state = state.copy(processed = state.processed + 1)
                    orphanScanStateFlow.value = state
                }

                state = state.copy(
                    status = "completed",
                    finishedAt = System.currentTimeMillis(),
                    error = null
                )
                orphanScanStateFlow.value = state
            } catch (e: Exception) {
                maintenanceLogger.warn("Orphan scan failed", e)
                state = state.copy(
                    status = "failed",
                    finishedAt = System.currentTimeMillis(),
                    error = e.message ?: "Orphan scan failed."
                )
                orphanScanStateFlow.value = state
            } finally {
                orphanScanMutex.withLock {
                    if (orphanScanJob === job) {
                        orphanScanJob = null
                    }
                }
            }
        }

        orphanScanJob = job
        return@withLock state
    }
}

suspend fun RoutingContext.scanOrphanedMediaHandler(metadataStorage: MetadataStorage) {
    val state = startOrphanScan(metadataStorage, call.application)
    call.respond(buildOrphanScanJson(state))
}

suspend fun RoutingContext.orphanScanStatusHandler() {
    call.respond(buildOrphanScanJson(orphanScanStateFlow.value))
}

suspend fun RoutingContext.deleteMarkedMediaHandler(
    metadataStorage: MetadataStorage,
    videoStorage: VideoStorage,
) {
    val summary = removeFilesScheduledForDeletion(metadataStorage, videoStorage)
    call.respond(buildJsonObject {
        put("totalMarked", summary.totalMarked)
        put("deleted", summary.deleted)
        put("failed", summary.failed)
    })
}

suspend fun triggerOrphanScan(metadataStorage: MetadataStorage, application: Application) {
    startOrphanScan(metadataStorage, application)
}
