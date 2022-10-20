package io.sebi.library

import io.sebi.creationTime
import io.sebi.datastructures.shaHashed
import io.sebi.phash.DHash
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
    var originPage: String? = null,
    val uid: String? = null,
    var hits: Int = 0,
    var markedForDeletion: Boolean = false
) {
    val id: String = uid ?: originUrl.shaHashed()

    val file: File? by lazy { File("./mediaLibrary/${id}/${id}.mp4") }
    val creationDate by lazy {
        if (file?.exists() == false) null
        else file?.creationTime?.toMillis()
    }

    fun withoutPage(): MediaLibraryEntry {
        return this.copy(originPage = null)
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

    @Transient
    private var _dhashes: List<DHash>? = null

    fun getDHashes(): List<DHash>? {
        if (_dhashes != null) return _dhashes

        val hashFile = File(file!!.parent, "dhashes.bin")
        if (!hashFile.exists()) return null
        val dhashes = hashFile.readULongs().map { DHash(it) }
        return if (dhashes.count() > 10) {
            _dhashes = dhashes
            dhashes
        } else {
            hashFile.delete() // less than ten hashes means something went wrong. maybe some interruption et al
            null
        }
    }

    fun addHitAndPersist(metadataStorage: MetadataStorage) {
        hits++
        persist(metadataStorage)
    }

    fun persist(metadataStorage: MetadataStorage) {
        metadataStorage.storeMetadata(id, this)
    }
}

@Serializable
data class AutoTaggedMediaLibraryEntry(val mediaLibraryEntry: MediaLibraryEntry, val autoTags: List<String>)