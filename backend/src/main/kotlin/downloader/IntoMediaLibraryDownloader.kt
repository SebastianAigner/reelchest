package io.sebi.downloader

import io.sebi.library.MediaLibrary
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.makeDownloadTask

class IntoMediaLibraryDownloader(private val downloadManager: DownloadManager, private val urlDecoder: UrlDecoder, private val mediaLibrary: MediaLibrary) {
    fun download(url: String) {
        downloadManager.enqueueTask(
            urlDecoder.makeDownloadTask(url, mediaLibrary::addCompletedDownload)
        )
    }
}