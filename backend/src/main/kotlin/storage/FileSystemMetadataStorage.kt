package io.sebi.storage

import io.sebi.library.MediaLibraryEntry
import io.sebi.library.json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileNotFoundException

class FileSystemMetadataStorage : MetadataStorage {
    override suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        val persisted = json.encodeToString(metadata)
        val targetFile = File("./mediaLibrary/${id}/${id}.json")
        targetFile.writeText(persisted)
    }

    override suspend fun retrieveMetadata(id: String): MetadataResult {
        val dir = File("./mediaLibrary/$id")
        if (!dir.exists()) return MetadataResult.None
        val meta = File(dir, "$id.json")
        if (!meta.exists()) return MetadataResult.Tombstone
        val entry = json.decodeFromString<MediaLibraryEntry>(meta.readText())
        return MetadataResult.Just(entry.copy(originPage = null, uid = entry.id))
    }

    override fun deleteMetadata(id: String) {
        try {
            File("./mediaLibrary/${id}/${id}.json").delete()
        } catch (f: FileNotFoundException) {
            // ... how convenient.
        }
    }

    override suspend fun listAllMetadata(): List<MediaLibraryEntry> {
        val mediaLibraryDirectory = File("./mediaLibrary/") // todo ew
        return mediaLibraryDirectory.list().mapNotNull { id ->
            (retrieveMetadata(id) as? MetadataResult.Just)?.entry
        }
    }
}