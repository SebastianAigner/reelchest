package io.sebi.utils

import io.ktor.http.content.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div

fun PartData.FileItem.copyTo(path: Path) {
    streamProvider().use { its -> path.toFile().outputStream().buffered().use { its.copyTo(it) } }
}

fun PartData.FileItem.copyToTempFile(prefix: String = ""): Path {
    val tempDir = Files.createTempDirectory(prefix)
    val targetFile: Path = tempDir / (this.originalFileName ?: UUID.randomUUID().toString())
    copyTo(targetFile)
    return targetFile
}