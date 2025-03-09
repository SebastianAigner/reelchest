package io.sebi.storage

import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.id
import io.sebi.library.json
import java.io.File
import java.io.FileNotFoundException

class FileSystemMetadataStorage : MetadataStorage {
    override suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        val persisted = json.encodeToString(metadata)
        val targetFile = File(AppConfig.mediaLibraryPath, "$id/$id.json")
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(persisted)
    }

    override suspend fun retrieveMetadata(id: String): MetadataResult {
        val dir = File(AppConfig.mediaLibraryPath, id)
        if (!dir.exists()) return MetadataResult.None
        val meta = File(dir, "$id.json")
        if (!meta.exists()) return MetadataResult.Tombstone
        val entry = json.decodeFromString<MediaLibraryEntry>(meta.readText())
        return MetadataResult.Just(entry.copy(uid = entry.id))
    }

    override suspend fun deleteMetadata(id: String) {
        try {
            File(AppConfig.mediaLibraryPath, "$id/$id.json").delete()
        } catch (f: FileNotFoundException) {
            // ... how convenient.
        }
    }

    override suspend fun listAllMetadata(): List<MediaLibraryEntry> {
        val mediaLibraryDirectory = File(AppConfig.mediaLibraryPath)
        return mediaLibraryDirectory.list().mapNotNull { id ->
            (retrieveMetadata(id) as? MetadataResult.Just)?.entry
        }
    }

    override suspend fun addDuplicate(id: String, dup: String, dist: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun getDuplicate(id: String): Duplicates? {
        TODO("Not yet implemented")
    }
}
