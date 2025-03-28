package io.sebi.storage

import io.sebi.library.MediaLibraryEntry
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class MultiplexingMetadataStorage(val metadataStorages: List<MetadataStorage>, val storageNames: List<String>) :
    MetadataStorage {
    val logger = LoggerFactory.getLogger("Multiplexing Metadata Storage")
    override suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        for (m in metadataStorages) {
            m.storeMetadata(id, metadata)
        }
    }

    override suspend fun retrieveMetadata(id: String): MetadataResult {
        val metadatas = metadataStorages.map { it.retrieveMetadata(id) }
        val single = metadatas.distinct().singleOrNull()
        return single ?: error("Mismatching metadata between multiplexed storages: ${metadatas.zip(storageNames)}")
    }

    override suspend fun deleteMetadata(id: String) {
        metadataStorages.forEach { it.deleteMetadata(id) }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun listAllMetadata(): List<MediaLibraryEntry> {
        val allMetadatas = metadataStorages.zip(storageNames).map { (individualStorage, individualStorageName) ->
            val (entries, time) = measureTimedValue {
                individualStorage.listAllMetadata()
            }
            logger.info("$individualStorageName took $time to list all metadata.")
            entries
        }
        val sizes = allMetadatas.map { it.size }
        require(allEqual(sizes)) { "All metadata storages should be in sync (${sizes.zip(storageNames)})." }
        return allMetadatas.first()
    }

    override suspend fun addDuplicate(id: String, dup: String, dist: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun getDuplicate(id: String): Duplicates? {
        TODO("Not yet implemented")
    }

    override suspend fun getTopDuplicates(limit: Int): List<Duplicates> {
        // Get top duplicates from all storages
        val allDuplicates = metadataStorages.flatMap { it.getTopDuplicates(limit) }

        // Sort by distance and take the top 'limit' entries
        return allDuplicates
            .sortedBy { it.distance }
            .take(limit)
    }
}

fun <T> allEqual(list: List<T>): Boolean {
    if (list.isEmpty()) return true
    val first = list.first()
    for (item in 1..list.lastIndex) {
        if (first != list[item]) return false
    }
    return true
}
