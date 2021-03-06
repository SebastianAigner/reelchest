package io.sebi

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.sebi.api.api
import io.sebi.autoscrape.setupAutoscraper
import io.sebi.downloader.DownloadManager
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.library.MediaLibrary
import io.sebi.network.NetworkManager
import io.sebi.phash.ensureDHashes
import io.sebi.setup.cleanupDownloadDirectory
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.shutdown.setupShutdownHooks
import io.sebi.storage.*
import io.sebi.tagging.CachingTagger
import io.sebi.tagging.Tagger
import io.sebi.ui.*
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.UrlDecoderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

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

    setupShutdownHooks(downloadManager)
    setupAutoscraper(mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)

    downloadManager.restoreQueue()
}


@Suppress("unused") // Referenced in application.conf
@JvmOverloads
fun Application.module() {
    File("userConfig").mkdir()
    File("mediaLibrary").mkdir()

    cleanupDownloadDirectory()

    val networkManager = NetworkManager()
    val urlDecoder: UrlDecoder = UrlDecoderImpl(networkManager)
    val videoStorage = FileSystemVideoStorage()

    val sqliteStorage = SqliteMetadataStorage()
    val fileSystemMetadataStorage = FileSystemMetadataStorage()

    SqliteMetadataImporter.import(fileSystemMetadataStorage, sqliteStorage)

    val metadataStorage =
        MultiplexingMetadataStorage(
            listOf(
                CachingMetadataStorage(fileSystemMetadataStorage),
                sqliteStorage,
            ),
            listOf("Caching[Filesystem]", "SQLite")
        )
    removeFilesScheduledForDeletion(metadataStorage, videoStorage)
    val mediaLibrary = MediaLibrary(urlDecoder, networkManager, videoStorage, metadataStorage)
    val downloadManager =
        DownloadManager(metadataStorage, urlDecoder, networkManager, mediaLibrary::addCompletedDownload)
    val duplicateCalculator = DuplicateCalculator(mediaLibrary)
    val tagger = CachingTagger()

    launch(Dispatchers.IO) {
        delay(60 * 30 * 1000)
        ensureDHashes(mediaLibrary)
    }

    runBlocking {
        setup(metadataStorage, mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)
    }

    launch(Dispatchers.Default) {
        delay(60 * 30 * 1000)
        duplicateCalculator.calculateDuplicates()
    }

    launch(Dispatchers.IO) {
        generateThumbnails(mediaLibrary)
    }

    installPlugins()

    routing {
        api(urlDecoder, mediaLibrary, duplicateCalculator, downloadManager, networkManager, tagger, metadataStorage)
        addDownload(downloadManager, urlDecoder, mediaLibrary::addCompletedDownload)
        addUpload(mediaLibrary)
        decryptEndpointRoute(urlDecoder)
        progressList(downloadManager)
        videoPlayer(mediaLibrary)
        setupStaticPaths()
    }
}

fun Routing.setupStaticPaths() {
    // Static feature. Try to access `/static/ktor_logo.svg`
    static("/static") {
        resources("static")
    }

    static("/") {
        resources("frontend")
        defaultResource("frontend/index.html")
    }

    defaultResource("frontend/index.html")
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