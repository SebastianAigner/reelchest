package io.sebi.storage

import io.sebi.library.id
import org.slf4j.LoggerFactory

object SqliteMetadataImporter {
    val logger = LoggerFactory.getLogger("Metadata Importer")
    suspend fun import(source: MetadataStorage, target: MetadataStorage) {
        val sourceEntries = source.listAllMetadata()
        val targetEntries = target.listAllMetadata()
        val idsInTarget = targetEntries.map { it.id }
        for (entry in sourceEntries) {
            if (entry.id in idsInTarget) continue

            logger.info("Imported ${entry.id} from disk to database.") // todo: technically, just from A to B, but that's what we'll be using it for. For exports, we might want to support the opposite.
            target.storeMetadata(entry.id, entry)
        }
    }
}