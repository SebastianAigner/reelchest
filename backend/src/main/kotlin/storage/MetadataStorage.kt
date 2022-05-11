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
    fun storeMetadata(id: String, metadata: MediaLibraryEntry)
    fun retrieveMetadata(id: String): MetadataResult
    fun deleteMetadata(id: String)
    fun listAllMetadata(): List<MediaLibraryEntry>
}