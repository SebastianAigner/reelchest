package io.sebi.phash

import io.sebi.ffmpeg.generateDHashes
import io.sebi.library.MediaLibrary

fun ensureDHashes(mediaLibrary: MediaLibrary) {
    mediaLibrary.entries.forEach {
        it.file?.let {
            if (it.parentFile?.list()?.none { it.startsWith("dhashes") } == true) {
                // this file is missing its dhashes!
                generateDHashes(it)
            }
        }
    }
}

