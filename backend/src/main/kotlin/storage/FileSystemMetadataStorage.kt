package io.sebi.storage

import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.id
import io.sebi.library.json
import java.io.File
import java.io.FileNotFoundException

class FileSystemMetadataStorage : MetadataStorage {
    private fun mediaInfoFile(id: String): File {
        return File(AppConfig.mediaLibraryPath, "$id/$id.media-info.json")
    }

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

    override suspend fun listTombstones(): List<String> {
        val mediaLibraryDirectory = File(AppConfig.mediaLibraryPath)
        return mediaLibraryDirectory.list().mapNotNull { id ->
            when (retrieveMetadata(id)) {
                MetadataResult.Tombstone -> id
                else -> null
            }
        }
    }

    override suspend fun getMediaInformation(id: String): MediaInformation? {
        val infoFile = mediaInfoFile(id)
        if (!infoFile.exists()) return null
        return json.decodeFromString(infoFile.readText())
    }

    override suspend fun upsertMediaInformation(info: MediaInformation) {
        val infoFile = mediaInfoFile(info.libraryEntryId)
        infoFile.parentFile?.mkdirs()
        infoFile.writeText(json.encodeToString(info))
    }

    override suspend fun searchMediaInformation(
        sort: MediaInfoSort,
        order: SortOrder,
        limit: Int,
    ): List<MediaInformationSummary> {
        val results = listAllMetadata().mapNotNull { entry ->
            val info = getMediaInformation(entry.id) ?: return@mapNotNull null
            if (info.probeStatus != MediaProbeStatus.SUCCESS) return@mapNotNull null
            MediaInformationSummary(
                id = entry.id,
                title = entry.name,
                originUrl = entry.originUrl,
                mimeType = info.mimeType,
                width = info.width,
                height = info.height,
                frameRate = info.frameRate,
                fileSize = info.fileSize,
                bitRate = info.bitRate,
                durationSeconds = info.durationSeconds
            )
        }
        val limited = results.sortedWith(mediaInfoComparator(sort, order)).take(limit)
        return limited
    }

    override suspend fun addDuplicate(id: String, dup: String, dist: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun getDuplicate(id: String): Duplicates? {
        TODO("Not yet implemented")
    }

    override suspend fun getTopDuplicates(limit: Int): List<Duplicates> {
        TODO("Not yet implemented")
    }
}

private fun mediaInfoComparator(sort: MediaInfoSort, order: SortOrder): Comparator<MediaInformationSummary> {
    fun sortValue(summary: MediaInformationSummary): Double {
        return when (sort) {
            MediaInfoSort.RESOLUTION -> (summary.width?.toDouble() ?: 0.0) * (summary.height?.toDouble() ?: 0.0)
            MediaInfoSort.FRAME_RATE -> summary.frameRate ?: 0.0
            MediaInfoSort.FILE_SIZE -> summary.fileSize?.toDouble() ?: 0.0
            MediaInfoSort.DURATION -> summary.durationSeconds ?: 0.0
            MediaInfoSort.WIDTH -> summary.width?.toDouble() ?: 0.0
            MediaInfoSort.HEIGHT -> summary.height?.toDouble() ?: 0.0
            MediaInfoSort.BIT_RATE -> summary.bitRate?.toDouble() ?: 0.0
        }
    }

    return Comparator { left, right ->
        val leftValue = sortValue(left)
        val rightValue = sortValue(right)
        val primary = if (order == SortOrder.DESC) {
            rightValue.compareTo(leftValue)
        } else {
            leftValue.compareTo(rightValue)
        }
        if (primary != 0) {
            primary
        } else {
            left.id.compareTo(right.id)
        }
    }
}
