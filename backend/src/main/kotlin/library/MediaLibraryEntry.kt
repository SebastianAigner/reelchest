package io.sebi.library

import io.sebi.datastructures.shaHashed
import io.sebi.phash.readULongs
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
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
)

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
val MediaLibraryEntry.file: File get() = File("./mediaLibrary/${id}/${id}.mp4")


@Serializable
data class AutoTaggedMediaLibraryEntry(val mediaLibraryEntry: MediaLibraryEntry, val autoTags: List<String>)