package io.sebi.storage

import io.sebi.library.MediaLibraryEntry

class CachingMetadataStorage(private val delegate: MetadataStorage) : MetadataStorage {
    val map = mutableMapOf<String, MetadataResult>()
    override fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        map[id] = MetadataResult.Just(metadata.copy(uid = metadata.id))
        delegate.storeMetadata(id, metadata)
    }

    override fun retrieveMetadata(id: String): MetadataResult {
        val cacheHit = map[id]
        if (cacheHit != null) return cacheHit

        val delegateHit = delegate.retrieveMetadata(id)
        map[id] = delegateHit
        return delegateHit
    }

    override fun deleteMetadata(id: String) {
        map.remove(id)
        delegate.deleteMetadata(id)
    }

    override fun listAllMetadata(): List<MediaLibraryEntry> {
        if (map.isNotEmpty()) return getAllRealEntries()

        val delegatedResult = delegate.listAllMetadata()
        for (entry in delegatedResult) {
            map[entry.id] = MetadataResult.Just(entry)
        }

        return getAllRealEntries()
    }

    private fun getAllRealEntries(): List<MediaLibraryEntry> {
        return map.values.filterIsInstance<MetadataResult.Just>().map { it.entry }
    }
}