package io.sebi.library

import io.sebi.config.AppConfig
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.ffmpeg.generateThumbnails
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import io.sebi.urldecoder.UrlDecoder
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import kotlin.random.Random

val json = Json

class MediaLibrary(
    private val urlDecoder: UrlDecoder,
    private val videoStorage: VideoStorage,
    private val metadataStorage: MetadataStorage,
) {
    suspend fun getEntries() = metadataStorage.listAllMetadata()
    val logger = LoggerFactory.getLogger("Media Library")
    val mediaLibraryScope = CoroutineScope(SupervisorJob())

    suspend fun findById(id: String): MediaLibraryEntry? {
        return metadataStorage.retrieveMetadata(id).just()
    }

    suspend fun existsOrTombstone(id: String): Boolean {
        return findById(id) != null || File(AppConfig.mediaLibraryPath, id).exists() // Smells like Null Object Pattern
    }

    suspend fun addUpload(f: File, name: String): String {
        logger.info("Adding upload $name!")
        val uid = Random.nextLong().toString().shaHashed()
        videoStorage.storeVideo(uid, f.toPath())
        val entry = MediaLibraryEntry(
            name = name,
            originUrl = "file://${Random.nextLong().toString().shaHashed()}}",
            tags = emptySet(),
            uid = uid,
            creationDate = System.currentTimeMillis() / 1000,
        )
        entry.persist(metadataStorage)
        f.delete()
        entry.file?.let {
            mediaLibraryScope.launch(Dispatchers.IO) {
                generateThumbnails(it)
            }
        }
        return uid
    }

    suspend fun addCompletedDownload(c: CompletedDownloadTask) {
        if (!c.targetFile.exists()) throw FileNotFoundException("Target File does not exist.")
        logger.info("Adding to media library: $c")
        val metadata = urlDecoder.getMetadata(c.originUrl)
        val uid = c.originUrl.shaHashed()
        val entry = MediaLibraryEntry(
            name = metadata?.title ?: c.originUrl,
            originUrl = c.originUrl,
            creationDate = System.currentTimeMillis() / 1000,
            tags = metadata?.tags?.toSet() ?: emptySet(),
        )
        withContext(Dispatchers.IO) {
            videoStorage.storeVideo(uid, c.targetFile.toPath())
            logger.info("Persisting $entry")
            entry.persist(metadataStorage)
            c.targetFile.delete()
            entry.file?.let {
                logger.info("Starting thumbnail generation.")
//            generateThumbnails(it)
            }
        }
    }
}
