package io.sebi.phash

import io.sebi.ffmpeg.generateDHashes
import io.sebi.library.MediaLibrary
import io.sebi.library.file
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun ensureDHashes(mediaLibrary: MediaLibrary) {
    val entriesWithFiles = mediaLibrary
        .entries
        .asSequence()
        .mapNotNull { it.file }

    val entriesWithoutHashes = entriesWithFiles
        .filter { file -> file.siblingFiles()?.none { it.startsWith("dhashes") } == true }

    entriesWithoutHashes
        .forEach { file ->
            withContext(Dispatchers.IO) {
                generateDHashes(file)
            }
        }
}

fun File.siblingFiles(): List<String>? {
    return this.parentFile?.list()?.toList()
}
