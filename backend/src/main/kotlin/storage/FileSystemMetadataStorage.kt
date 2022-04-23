package io.sebi.storage

import io.sebi.library.MediaLibraryEntry
import io.sebi.library.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileNotFoundException

class FileSystemMetadataStorage : MetadataStorage {
    override fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        val persisted = json.encodeToString(metadata)
        val targetFile = File("./mediaLibrary/${id}/${id}.json")
        targetFile.writeText(persisted)
    }

    override fun retrieveMetadata(id: String): MetadataResult {
        val dir = File("./mediaLibrary/$id")
        if (!dir.exists()) return MetadataResult.None
        val meta = File(dir, "$id.json")
        if (!meta.exists()) return MetadataResult.Tombstone
        return MetadataResult.Just(json.decodeFromString<MediaLibraryEntry>(meta.readText()).copy(originPage = null))
    }

    override fun deleteMetadata(id: String) {
        try {
            File("./mediaLibrary/${id}/${id}.json").delete()
        } catch (f: FileNotFoundException) {
            // ... how convenient.
        }
    }

    override fun listAllMetadata(): List<MediaLibraryEntry> {
        val mediaLibraryDirectory = File("./mediaLibrary/") // todo ew
        return mediaLibraryDirectory.list().mapNotNull { id ->
            (retrieveMetadata(id) as? MetadataResult.Just)?.entry
        }
    }
}