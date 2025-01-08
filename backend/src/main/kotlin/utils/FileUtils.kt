package io.sebi.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

val File.creationTime: FileTime
    get() = Files.readAttributes(this.toPath(), BasicFileAttributes::class.java).creationTime()


fun List<Path>.createZipFile(destination: Path = Files.createTempFile("temp", ".zip")): Path {
    Files.newOutputStream(destination).use { outputStream ->
        java.util.zip.ZipOutputStream(outputStream).use { zipStream ->
            forEach { path ->
                val zipEntry = java.util.zip.ZipEntry(path.fileName.toString())
                zipStream.putNextEntry(zipEntry)
                Files.newInputStream(path).use { inputStream ->
                    inputStream.copyTo(zipStream)
                }
                zipStream.closeEntry()
            }
        }
    }
    return destination
}