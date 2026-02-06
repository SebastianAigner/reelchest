package io.sebi.downloader

fun interface DownloadStartGate {
    suspend fun awaitPermit()
}

object AllowAllDownloadStartGate : DownloadStartGate {
    override suspend fun awaitPermit() = Unit
}
