package io.sebi.storage

import io.sebi.library.MediaLibraryEntry

sealed class MetadataResult {
    abstract fun just(): MediaLibraryEntry?

    object None : MetadataResult() {
        override fun just(): MediaLibraryEntry? = null
    }

    object Tombstone : MetadataResult() {
        override fun just(): MediaLibraryEntry? = null
    }

    data class Just(val entry: MediaLibraryEntry) : MetadataResult() {
        override fun just(): MediaLibraryEntry = entry
    }
}

interface MetadataStorage {
    suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry)
    suspend fun retrieveMetadata(id: String): MetadataResult
    suspend fun deleteMetadata(id: String)
    suspend fun listAllMetadata(): List<MediaLibraryEntry>
    suspend fun addDuplicate(id: String, dup: String, dist: Int)
    suspend fun getDuplicate(id: String): Duplicates?
    suspend fun getTopDuplicates(limit: Int = 100): List<Duplicates>
}
