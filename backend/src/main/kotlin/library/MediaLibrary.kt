package io.sebi.library

import io.sebi.datastructures.shaHashed
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.ffmpeg.generateThumbnails
import io.sebi.network.NetworkManager
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import io.sebi.urldecoder.UrlDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import kotlin.random.Random

val json = Json

class MediaLibrary(
    private val urlDecoder: UrlDecoder,
    private val networkManager: NetworkManager,
    private val videoStorage: VideoStorage,
    private val metadataStorage: MetadataStorage
) {
    val entries get() = metadataStorage.listAllMetadata()
    val logger = LoggerFactory.getLogger("Media Library")

    fun findById(id: String): MediaLibraryEntry? {
        return metadataStorage.retrieveMetadata(id).just()
    }

    fun existsOrTombstone(id: String): Boolean {
        return findById(id) != null || File("./mediaLibrary/${id}").exists() // Smells like Null Object Pattern
    }

    fun addUpload(f: File, name: String) {
        logger.info("Adding upload $name!")
        val uid = Random.nextLong().toString().shaHashed()
        videoStorage.storeVideo(uid, f.toPath())
        val entry = MediaLibraryEntry(
            name = name,
            originUrl = "file://${Random.nextLong().toString().shaHashed()}}",
            tags = emptySet(),
            uid = uid,
            originPage = null
        )
        entry.persist(metadataStorage)
        f.delete()
        entry.file?.let {
            GlobalScope.launch(Dispatchers.IO) {
                generateThumbnails(it)
            }
        }
    }

    suspend fun addCompletedDownload(c: CompletedDownloadTask) {
        if (!c.targetFile.exists()) throw FileNotFoundException("Target File does not exist.")
        logger.info("Adding to media library: $c")
        val metadata = urlDecoder.getMetadata(c.originUrl)
        val uid = c.originUrl.shaHashed()
        val entry = MediaLibraryEntry(
            name = metadata?.title ?: c.originUrl,
            originUrl = c.originUrl,
            tags = metadata?.tags?.toSet() ?: emptySet(),
        )

        videoStorage.storeVideo(uid, c.targetFile.toPath())
        logger.info("Persisting $entry")
        entry.persist(metadataStorage)
        c.targetFile.delete()
        entry.file?.let {
            logger.info("Starting thumbnail generation.")
            generateThumbnails(it)
        }
    }
}



