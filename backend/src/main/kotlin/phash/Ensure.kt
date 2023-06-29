package io.sebi.phash

import io.sebi.ffmpeg.generateDHashes
import io.sebi.library.MediaLibrary
import kotlinx.coroutines.yield

suspend fun ensureDHashes(mediaLibrary: MediaLibrary) {
    mediaLibrary.entries.forEach {
        yield()
        it.file?.let {
            if (it.parentFile?.list()?.none { it.startsWith("dhashes") } == true) {
                // this file is missing its dhashes!
                generateDHashes(it)
            }
        }
    }
}

