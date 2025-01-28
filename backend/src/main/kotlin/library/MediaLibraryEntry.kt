package io.sebi.library

import io.sebi.config.AppConfig
import io.sebi.datastructures.shaHashed
import io.sebi.ffmpeg.getMediaType
import io.sebi.phash.readULongs
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File


@Serializable
data class MediaLibraryEntry(
    var name: String,
    val originUrl: String,
    var tags: Set<String> = emptySet(),
    val creationDate: Long,
    val uid: String? = null,
    var hits: Int = 0,
    var markedForDeletion: Boolean = false,
) {
    @SerialName("id")
    val unifiedId: String = this.id
}

@OptIn(ExperimentalUnsignedTypes::class)
fun MediaLibraryEntry.getDHashesFromDisk(): ULongArray? {
    val hashFile = File(file!!.parent, "dhashes.bin")
    if (!hashFile.exists()) return null
    val dhashes = hashFile.readULongs()
    return if (dhashes.count() > 10) {
        dhashes
    } else {
        hashFile.delete() // less than ten hashes means something went wrong. maybe some interruption et al
        null
    }
}

suspend fun MediaLibraryEntry.addHitAndPersist(metadataStorage: MetadataStorage) {
    hits++
    persist(metadataStorage)
}

suspend fun MediaLibraryEntry.persist(metadataStorage: MetadataStorage) {
    metadataStorage.storeMetadata(id, this)
}

fun MediaLibraryEntry.getThumbnails(): List<File>? {
    return this.file
        ?.parentFile
        ?.listFiles()
        ?.filter { it.name.startsWith("thumb") }
}

fun MediaLibraryEntry.withAutoTags(tagger: Tagger): AutoTaggedMediaLibraryEntry {
    return AutoTaggedMediaLibraryEntry(this, tagger.tag(this.name, this.tags).toList())
}

val MediaLibraryEntry.id: String get() = uid ?: originUrl.shaHashed()
val MediaLibraryEntry.file: File get() = File(AppConfig.mediaLibraryPath, "${id}/${id}.mp4")


@Serializable
data class AutoTaggedMediaLibraryEntry(val mediaLibraryEntry: MediaLibraryEntry, val autoTags: List<String>)

suspend fun MediaLibraryEntry.getMimeType(): String {
    val mediaFile = this.file
    if (!mediaFile.exists()) {
        throw IllegalStateException("Media file does not exist: ${mediaFile.absolutePath}")
    }

    return try {
        val mediaInfo = getMediaType(mediaFile)
        when (mediaInfo.codecType.lowercase()) {
            "video" -> "video/${mediaInfo.codecName.lowercase()}"
            "audio" -> "audio/${mediaInfo.codecName.lowercase()}"
            else -> "application/octet-stream"
        }
    } catch (e: Exception) {
        throw IllegalStateException("Failed to determine MIME type for ${mediaFile.absolutePath}", e)
    }
}
