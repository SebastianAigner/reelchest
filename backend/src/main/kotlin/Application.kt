package io.sebi

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.sebi.api.api
import io.sebi.api.feedApi
import io.sebi.autoscrape.setupAutoscraper
import io.sebi.downloader.DownloadManager
import io.sebi.downloader.IntoMediaLibraryDownloader
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.library.MediaLibrary
import io.sebi.network.NetworkManager
import io.sebi.phash.ensureDHashes
import io.sebi.setup.cleanupDownloadDirectory
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.storage.FileSystemVideoStorage
import io.sebi.storage.MetadataStorage
import io.sebi.storage.SqliteMetadataStorage
import io.sebi.tagging.CachingTagger
import io.sebi.tagging.Tagger
import io.sebi.ui.*
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.UrlDecoderImpl
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun setup(
    metadataStorage: MetadataStorage,
    mediaLibrary: MediaLibrary,
    downloadManager: DownloadManager,
    networkManager: NetworkManager,
    tagger: Tagger,
    urlDecoder: UrlDecoder,
) {
    val logger = LoggerFactory.getLogger("Setup")
    System.getenv("DLMGR_TMP_DIR")?.let {
        System.setProperty("java.io.tmpdir", it)
    }

    logger.info("Temporary directory is " + System.getProperty("java.io.tmpdir"))
    logger.info("Working directory is " + System.getProperty("user.dir"))

    setupAutoscraper(mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)

    downloadManager.restoreQueue()
}


@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    File("userConfig").mkdir()
    File("mediaLibrary").mkdir()

    cleanupDownloadDirectory()

    val networkManager = NetworkManager()
    val urlDecoder: UrlDecoder = UrlDecoderImpl(networkManager)
    val videoStorage = FileSystemVideoStorage()

    val metadataStorage: MetadataStorage = SqliteMetadataStorage()
    runBlocking { removeFilesScheduledForDeletion(metadataStorage, videoStorage) }
    val mediaLibrary = MediaLibrary(urlDecoder, networkManager, videoStorage, metadataStorage)
    val downloadManager =
        DownloadManager(
            metadataStorage,
            urlDecoder,
            networkManager,
            mediaLibrary::addCompletedDownload,
            environment.monitor
        )
    val duplicateCalculator = DuplicateCalculator(mediaLibrary)
    val tagger = CachingTagger()
    val intoMediaLibraryDownloader = IntoMediaLibraryDownloader(downloadManager, urlDecoder, mediaLibrary)

    launch(Dispatchers.IO) {
        delay(45.minutes)
        ensureDHashes(mediaLibrary)
    }

    runBlocking {
        setup(metadataStorage, mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)
    }

    launch(Dispatchers.Default) {
        delay(50.minutes)
        duplicateCalculator.calculateDuplicates()
    }

    launch(Dispatchers.IO) {
        delay(0.minutes)
        generateThumbnails(mediaLibrary)
    }

    installPlugins()

    routing {
        api(urlDecoder, mediaLibrary, duplicateCalculator, downloadManager, networkManager, tagger, metadataStorage)
        addDownload(intoMediaLibraryDownloader)
        feedApi(Feed(), intoMediaLibraryDownloader)
        addUpload(mediaLibrary)
        decryptEndpointRoute(urlDecoder)
        progressList(downloadManager)
        videoPlayer(mediaLibrary)
        setupStaticPaths()
    }

    launch {
        warmApi(environment as ApplicationEngineEnvironment, this)
    }
}

fun warmApi(env: ApplicationEngineEnvironment, scope: CoroutineScope) {
    env.monitor.subscribe(ServerReady) {
        val conns = env.connectors.first()
        val url = "http://${conns.host}:${conns.port}/api/mediaLibrary"
        scope.launch {
            HttpClient().get(url)
        }
    }
}

fun Routing.setupStaticPaths() {
    staticResources("/static", "static")
    staticResources("/", "frontend")
}

private fun generateThumbnails(mediaLibrary: MediaLibrary) {
    println("starting thumbnail generation")
    mediaLibrary.entries.forEach {
        it.file?.let {
            if (it.parentFile?.list()?.none { it.startsWith("thumb") } == true) {
                val proc = ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i",
                    it.name,
                    "-q:v",
                    "5",
                    "-vf",
                    "fps=1/60",
                    "thumb%04d.jpg",
                ).directory(it.parentFile)
                    .inheritIO()
                    .start()

                proc.waitFor(60, TimeUnit.SECONDS)
                proc.destroy()
                proc.waitFor()
            }
        }
    }
}