package io.sebi.library

import io.sebi.config.AppConfig
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.ffmpeg.MediaProbeInfo
import io.sebi.ffmpeg.generateThumbnails
import io.sebi.ffmpeg.getMediaProbeInfo
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
        val mediaFile = entry.file
        mediaLibraryScope.launch(Dispatchers.IO) {
            generateThumbnails(mediaFile)
        }
        return uid
    }

    suspend fun addCompletedDownload(c: CompletedDownloadTask) {
        if (!c.targetFile.exists()) throw FileNotFoundException("Target File does not exist.")
        logger.info("Adding to media library: $c")
        validateDownloadedMedia(c)
        val metadata = try {
            urlDecoder.getMetadata(c.originUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            rejectDownload(c, "Metadata lookup failed for ${c.originUrl}: ${error.message}", error)
        }
        val uid = c.originUrl.shaHashed()
        val entry = MediaLibraryEntry(
            name = metadata?.title ?: c.originUrl,
            originUrl = c.originUrl,
            creationDate = System.currentTimeMillis() / 1000,
            tags = metadata?.tags?.toSet() ?: emptySet(),
        )
        try {
            withContext(Dispatchers.IO) {
                videoStorage.storeVideo(uid, c.targetFile.toPath())
                logger.info("Persisting $entry")
                entry.persist(metadataStorage)
                c.targetFile.delete()
                logger.info("Starting thumbnail generation.")
//            generateThumbnails(entry.file)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("Failed to persist download for ${c.originUrl}", error)
            cleanupRejectedDownload(c)
            cleanupPartialLibraryEntry(uid)
            throw IllegalStateException(
                "Failed to persist downloaded media for ${c.originUrl}: ${error.message}",
                error
            )
        }
    }

    private suspend fun validateDownloadedMedia(c: CompletedDownloadTask): MediaProbeInfo {
        val probeInfo = try {
            withContext(Dispatchers.IO) {
                getMediaProbeInfo(c.targetFile)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            rejectDownload(
                c,
                "Downloaded file for ${c.originUrl} failed ffprobe validation: ${error.message}",
                error
            )
        }
        val codecType = probeInfo.mediaTypeInfo.codecType
        if (!isSupportedMediaCodecType(codecType)) {
            rejectDownload(
                c,
                "Downloaded file for ${c.originUrl} is not audio or video (ffprobe codec_type=$codecType)."
            )
        }
        return probeInfo
    }

    private fun isSupportedMediaCodecType(codecType: String): Boolean {
        return codecType.equals("video", ignoreCase = true) || codecType.equals("audio", ignoreCase = true)
    }

    private fun rejectDownload(c: CompletedDownloadTask, message: String, error: Exception? = null): Nothing {
        if (error != null) {
            logger.warn("Rejected download for ${c.originUrl}. $message", error)
        } else {
            logger.warn("Rejected download for ${c.originUrl}. $message")
        }
        cleanupRejectedDownload(c)
        throw IllegalStateException(message, error)
    }

    private fun cleanupRejectedDownload(c: CompletedDownloadTask) {
        if (!c.targetFile.exists()) return
        try {
            if (!c.targetFile.delete()) {
                logger.warn("Failed to delete rejected download ${c.targetFile.absolutePath}")
            }
        } catch (error: Exception) {
            logger.warn("Failed to delete rejected download ${c.targetFile.absolutePath}", error)
        }
    }

    private suspend fun cleanupPartialLibraryEntry(uid: String) {
        try {
            metadataStorage.deleteMetadata(uid)
        } catch (error: Exception) {
            logger.warn("Failed to delete metadata for rejected download $uid", error)
        }
        try {
            videoStorage.deleteVideo(uid)
        } catch (error: Exception) {
            logger.warn("Failed to delete media file for rejected download $uid", error)
        }
    }
}
