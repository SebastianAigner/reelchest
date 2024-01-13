package io.sebi.phash

import io.sebi.ffmpeg.generateDHashes
import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun ensureDHashes(mediaLibrary: MediaLibrary) {
    mediaLibrary.entries.forEach { entry: MediaLibraryEntry ->
        withContext(Dispatchers.IO) {
            entry.file?.let { file: File ->
                if (file.parentFile?.list()?.none { it.startsWith("dhashes") } == true) {
                    // this file is missing its dhashes!
                    generateDHashes(file)
                }
            }
        }
    }
}

