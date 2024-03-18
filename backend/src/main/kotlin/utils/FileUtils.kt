package io.sebi.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

val File.creationTime: FileTime
    get() = Files.readAttributes(this.toPath(), BasicFileAttributes::class.java).creationTime()