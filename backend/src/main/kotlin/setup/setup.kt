package io.sebi.setup

import io.sebi.library.MediaLibraryEntry
import io.sebi.library.id
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("Setup")
fun cleanupDownloadDirectory() {
    File("downloads").listFiles()?.forEach {
        it.deleteRecursively()
    }
}

suspend fun removeFilesScheduledForDeletion(metadataStorage: MetadataStorage, videoStorage: VideoStorage) {
    metadataStorage.listAllMetadata().filter(MediaLibraryEntry::markedForDeletion).forEach {
        videoStorage.deleteVideo(it.id)
        metadataStorage.deleteMetadata(it.id)
    }
}