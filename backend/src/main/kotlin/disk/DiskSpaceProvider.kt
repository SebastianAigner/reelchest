package io.sebi.disk

import io.sebi.config.AppConfig
import java.io.File

fun interface DiskSpaceProvider {
    fun usableBytes(): Long
}

class MediaLibraryDiskSpaceProvider : DiskSpaceProvider {
    override fun usableBytes(): Long {
        return File(AppConfig.mediaLibraryPath).usableSpace
    }
}
