package io.sebi.downloader


interface DownloadManager {
    fun getDownloads(vararg d: DownloadType): List<DownloadTask>
    fun downloadProgressForId(id: String): Double?
    fun enqueueTask(d: DownloadTask, skipDuplicatesCheck: Boolean = false)
    fun persistQueue()
    fun restoreQueue()
    val finishedDownloads: Iterable<CompletedDownloadTask>
    val problematicDownloads: MutableList<ProblematicTask>
    val queuedDownloads: Iterable<DownloadTask>
    val allDownloads: List<WithOriginUrl>
    val downloadsInProgress: List<DownloadTask>
}

enum class DownloadType {
    QUEUED, CURRENT,
}

