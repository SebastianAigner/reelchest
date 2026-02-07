package io.sebi.downloader

sealed class DownloadEnqueueResult {
    data object Enqueued : DownloadEnqueueResult()
    data class Skipped(val reason: String) : DownloadEnqueueResult()
}
