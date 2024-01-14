package io.sebi.storage

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileSystemVideoStorage : VideoStorage {
    private val logger = LoggerFactory.getLogger("File System Video Storage")

    override fun storeVideo(id: String, videoFile: Path) {
        logger.info("Adding to media library: $id")
        val mediaLibraryFileTarget = File("./mediaLibrary/${id}/${id}.mp4")
        mediaLibraryFileTarget.parentFile.mkdirs()
        logger.info("Beginning to copy $id to target location...")
        Files.copy(videoFile, mediaLibraryFileTarget.toPath(), StandardCopyOption.REPLACE_EXISTING)
        logger.info("...target location copy done.")
    }

    override fun deleteVideo(id: String) {
        val entryDir = File("./mediaLibrary/$id")
        entryDir.listFiles()?.forEach { filesInFolder ->
            filesInFolder.deleteRecursively()
            // We keep the directory around as a tombstone to make sure we don't keep on downloading the same content.
        }
    }
}