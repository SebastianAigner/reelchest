package io.sebi.api.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.config.AppConfig
import io.sebi.ffmpeg.getMediaProbeInfo
import io.sebi.ffmpeg.toMimeType
import io.sebi.library.file
import io.sebi.library.id
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.storage.MediaInformation
import io.sebi.storage.MediaProbeStatus
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

private data class MediaInfoPopulationState(
    val status: String,
    val totalEntries: Int,
    val processed: Int,
    val probed: Int,
    val skipped: Int,
    val succeeded: Int,
    val failed: Int,
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

private fun idleMediaInfoPopulationState() = MediaInfoPopulationState(
    status = "idle",
    totalEntries = 0,
    processed = 0,
    probed = 0,
    skipped = 0,
    succeeded = 0,
    failed = 0,
    startedAt = null,
    finishedAt = null,
    error = null
)

private val orphanScanMutex = Mutex()
private val orphanScanStateFlow = MutableStateFlow(idleOrphanScanState())
private var orphanScanJob: Job? = null
private val mediaInfoPopulationMutex = Mutex()
private val mediaInfoPopulationStateFlow = MutableStateFlow(idleMediaInfoPopulationState())
private var mediaInfoPopulationJob: Job? = null

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

private fun buildMediaInfoPopulationJson(state: MediaInfoPopulationState) = buildJsonObject {
    put("status", state.status)
    put("totalEntries", state.totalEntries)
    put("processed", state.processed)
    put("probed", state.probed)
    put("skipped", state.skipped)
    put("succeeded", state.succeeded)
    put("failed", state.failed)
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

private suspend fun startMediaInfoPopulation(
    metadataStorage: MetadataStorage,
    application: Application,
): MediaInfoPopulationState {
    return mediaInfoPopulationMutex.withLock {
        val currentJob = mediaInfoPopulationJob
        if (currentJob != null && currentJob.isActive) {
            return@withLock mediaInfoPopulationStateFlow.value
        }

        val entries = metadataStorage.listAllMetadata()
        val startedAt = System.currentTimeMillis()
        var state = MediaInfoPopulationState(
            status = "running",
            totalEntries = entries.size,
            processed = 0,
            probed = 0,
            skipped = 0,
            succeeded = 0,
            failed = 0,
            startedAt = startedAt,
            finishedAt = null,
            error = null
        )
        mediaInfoPopulationStateFlow.value = state

        lateinit var job: Job
        job = application.launch(Dispatchers.IO) {
            try {
                entries.forEach { entry ->
                    val existing = metadataStorage.getMediaInformation(entry.id)
                    if (existing != null) {
                        state = state.copy(
                            processed = state.processed + 1,
                            skipped = state.skipped + 1
                        )
                        mediaInfoPopulationStateFlow.value = state
                        return@forEach
                    }

                    val mediaFile = entry.file
                    if (!mediaFile.exists()) {
                        metadataStorage.upsertMediaInformation(
                            MediaInformation(
                                libraryEntryId = entry.id,
                                mimeType = null,
                                codecType = null,
                                codecName = null,
                                width = null,
                                height = null,
                                bitRate = null,
                                frameRate = null,
                                fileSize = null,
                                durationSeconds = null,
                                probeStatus = MediaProbeStatus.FAILED,
                                probeError = "File not found",
                                lastProbeAt = System.currentTimeMillis(),
                            )
                        )
                        state = state.copy(
                            processed = state.processed + 1,
                            failed = state.failed + 1
                        )
                        mediaInfoPopulationStateFlow.value = state
                        return@forEach
                    }

                    try {
                        val probeInfo = getMediaProbeInfo(mediaFile)
                        val mediaType = probeInfo.mediaTypeInfo
                        val mimeType = mediaType.toMimeType()
                        metadataStorage.upsertMediaInformation(
                            MediaInformation(
                                libraryEntryId = entry.id,
                                mimeType = mimeType,
                                codecType = mediaType.codecType,
                                codecName = mediaType.codecName,
                                width = mediaType.width,
                                height = mediaType.height,
                                bitRate = mediaType.bitRate,
                                frameRate = mediaType.frameRate,
                                fileSize = mediaType.fileSize,
                                durationSeconds = probeInfo.durationSeconds,
                                probeStatus = MediaProbeStatus.SUCCESS,
                                probeError = null,
                                lastProbeAt = System.currentTimeMillis(),
                            )
                        )
                        state = state.copy(
                            processed = state.processed + 1,
                            probed = state.probed + 1,
                            succeeded = state.succeeded + 1
                        )
                        mediaInfoPopulationStateFlow.value = state
                    } catch (e: Exception) {
                        metadataStorage.upsertMediaInformation(
                            MediaInformation(
                                libraryEntryId = entry.id,
                                mimeType = null,
                                codecType = null,
                                codecName = null,
                                width = null,
                                height = null,
                                bitRate = null,
                                frameRate = null,
                                fileSize = null,
                                durationSeconds = null,
                                probeStatus = MediaProbeStatus.FAILED,
                                probeError = e.message ?: "Failed to determine media information",
                                lastProbeAt = System.currentTimeMillis(),
                            )
                        )
                        state = state.copy(
                            processed = state.processed + 1,
                            probed = state.probed + 1,
                            failed = state.failed + 1
                        )
                        mediaInfoPopulationStateFlow.value = state
                    }
                }

                state = state.copy(
                    status = "completed",
                    finishedAt = System.currentTimeMillis(),
                    error = null
                )
                mediaInfoPopulationStateFlow.value = state
            } catch (e: Exception) {
                maintenanceLogger.warn("Media info population failed", e)
                state = state.copy(
                    status = "failed",
                    finishedAt = System.currentTimeMillis(),
                    error = e.message ?: "Media info population failed."
                )
                mediaInfoPopulationStateFlow.value = state
            } finally {
                mediaInfoPopulationMutex.withLock {
                    if (mediaInfoPopulationJob === job) {
                        mediaInfoPopulationJob = null
                    }
                }
            }
        }

        mediaInfoPopulationJob = job
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

suspend fun RoutingContext.populateMediaInformationHandler(metadataStorage: MetadataStorage) {
    val state = startMediaInfoPopulation(metadataStorage, call.application)
    call.respond(buildMediaInfoPopulationJson(state))
}

suspend fun RoutingContext.mediaInformationPopulationStatusHandler() {
    call.respond(buildMediaInfoPopulationJson(mediaInfoPopulationStateFlow.value))
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
