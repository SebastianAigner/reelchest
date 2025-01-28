package io.sebi.setup

import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.id
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("Setup")
fun cleanupDownloadDirectory() {
    val downloadsDir = File(AppConfig.downloadsPath)
    if (downloadsDir.exists()) {
        downloadsDir.listFiles()?.forEach {
            it.deleteRecursively()
        }
    }
}

suspend fun removeFilesScheduledForDeletion(metadataStorage: MetadataStorage, videoStorage: VideoStorage) {
    metadataStorage.listAllMetadata().filter(MediaLibraryEntry::markedForDeletion).forEach {
        videoStorage.deleteVideo(it.id)
        metadataStorage.deleteMetadata(it.id)
    }
}
