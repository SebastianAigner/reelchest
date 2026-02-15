package io.sebi.setup

import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.id
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("Setup")
private val deletionMutex = Mutex()

data class DeletionSummary(
    val totalMarked: Int,
    val deleted: Int,
    val failed: Int,
)
fun cleanupDownloadDirectory() {
    val downloadsDir = File(AppConfig.downloadsPath)
    if (downloadsDir.exists()) {
        downloadsDir.listFiles()?.forEach {
            it.deleteRecursively()
        }
    }
}

suspend fun removeFilesScheduledForDeletion(
    metadataStorage: MetadataStorage,
    videoStorage: VideoStorage,
): DeletionSummary {
    return deletionMutex.withLock {
        val markedEntries = metadataStorage.listAllMetadata().filter(MediaLibraryEntry::markedForDeletion)
        var deleted = 0
        var failed = 0
        markedEntries.forEach { entry ->
            try {
                videoStorage.deleteVideo(entry.id)
                metadataStorage.deleteMetadata(entry.id)
                deleted++
            } catch (e: Exception) {
                logger.warn("Failed to delete marked entry ${entry.id}", e)
                failed++
            }
        }
        DeletionSummary(
            totalMarked = markedEntries.size,
            deleted = deleted,
            failed = failed
        )
    }
}
