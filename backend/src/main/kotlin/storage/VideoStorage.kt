package io.sebi.storage

import java.nio.file.Path

interface VideoStorage {
    fun storeVideo(id: String, videoFile: Path)
    fun deleteVideo(id: String)
}