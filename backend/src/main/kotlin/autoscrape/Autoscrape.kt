package io.sebi.autoscrape

import io.sebi.datastructures.shaHashed
import io.sebi.downloader.DownloadManager
import io.sebi.library.MediaLibrary
import io.sebi.network.NetworkManager
import io.sebi.search.SearcherFactory
import io.sebi.tagging.Tagger
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.makeDownloadTask
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.InputType
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("Scraper")
fun setupAutoscraper(
    mediaLibrary: MediaLibrary,
    downloadManager: DownloadManager,
    networkManager: NetworkManager,
    tagger: Tagger,
    urlDecoder: UrlDecoder
) {
    GlobalScope.launch {
        delay(3600_000)
        scrape(mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)
    }
}

private suspend fun scrape(
    mediaLibrary: MediaLibrary,
    downloadManager: DownloadManager,
    networkManager: NetworkManager,
    tagger: Tagger,
    urlDecoder: UrlDecoder
) {
    logger.info("Starting scrape...")
    val queries = File("userConfig/queries.txt").readLines()
    val searcher = queries.first()
    queries.drop(1).forEach {
        logger.info("Triggered search for $it")

        val results = SearcherFactory.byEndpointName(searcher, networkManager).search(it)
        // TODO: This approach just checks the three freshest results. If new unloaded results arrive at a faster pace,
        //  it would make sense to adjust this checking approach.
        val unloadedResults = results.take(3).filterNot { searchResult ->
            mediaLibrary.existsOrTombstone(searchResult.url.shaHashed())
        }
        logger.info("${unloadedResults.count()} unloaded results.")
        unloadedResults.forEach { searchResult ->
            logger.info("Requesting enqueueing of ${searchResult.title} ${InputType.url}")
            downloadManager.enqueueTask(
                urlDecoder.makeDownloadTask(searchResult.url, mediaLibrary::addCompletedDownload)
            )
        }
        logger.info("Pausing before next search.")
        delay(60_000 * Random.nextLong(1, 10)) // no need to hurry.
    }
}