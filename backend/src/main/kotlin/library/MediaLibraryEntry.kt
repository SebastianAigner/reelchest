package io.sebi.library

import io.sebi.datastructures.shaHashed
import io.sebi.phash.readULongs
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

@Serializable
data class MediaLibraryEntry(
    var name: String,
    val originUrl: String,
    var tags: Set<String> = emptySet(),
    val creationDate: Long,
    var originPage: String? = null,
    val uid: String? = null,
    var hits: Int = 0,
    var markedForDeletion: Boolean = false,
) {
    val id: String = uid ?: originUrl.shaHashed()

    val file: File? by lazy { File("./mediaLibrary/${id}/${id}.mp4") }

    fun withoutPage(): MediaLibraryEntry {
        return if (originPage != null) this.copy(originPage = null) else this
    }

    fun withAutoTags(tagger: Tagger): AutoTaggedMediaLibraryEntry {
        return AutoTaggedMediaLibraryEntry(this, tagger.tag(this.name, this.tags).toList())
    }

    fun getThumbnails(): List<File>? {
        return this.file
            ?.parentFile
            ?.listFiles()
            ?.filter { it.name.startsWith("thumb") }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Transient
    private var _dhashes: ULongArray? = null

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getDHashes(): ULongArray? {
        if (_dhashes != null) return _dhashes

        val hashFile = File(file!!.parent, "dhashes.bin")
        if (!hashFile.exists()) return null
        val dhashes = hashFile.readULongs()
        return if (dhashes.count() > 10) {
            _dhashes = dhashes
            dhashes
        } else {
            hashFile.delete() // less than ten hashes means something went wrong. maybe some interruption et al
            null
        }
    }

    suspend fun addHitAndPersist(metadataStorage: MetadataStorage) {
        hits++
        persist(metadataStorage)
    }

    suspend fun persist(metadataStorage: MetadataStorage) {
        metadataStorage.storeMetadata(id, this)
    }
}

@Serializable
data class AutoTaggedMediaLibraryEntry(val mediaLibraryEntry: MediaLibraryEntry, val autoTags: List<String>)