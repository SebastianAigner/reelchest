package io.sebi

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.sebi.api.api
import io.sebi.autoscrape.setupAutoscraper
import io.sebi.downloader.DownloadManager
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.library.MediaLibrary
import io.sebi.migrations.MigrationManager
import io.sebi.network.NetworkManager
import io.sebi.phash.ensureDHashes
import io.sebi.setup.cleanupDownloadDirectory
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.shutdown.setupShutdownHooks
import io.sebi.storage.CachingMetadataStorage
import io.sebi.storage.FileSystemMetadataStorage
import io.sebi.storage.FileSystemVideoStorage
import io.sebi.storage.MetadataStorage
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
    urlDecoder: UrlDecoder
) {
    val logger = LoggerFactory.getLogger("Setup")
    System.getenv("DLMGR_TMP_DIR")?.let {
        System.setProperty("java.io.tmpdir", it)
    }

    logger.info("Temporary directory is " + System.getProperty("java.io.tmpdir"))
    logger.info("Working directory is " + System.getProperty("user.dir"))

    MigrationManager(metadataStorage, mediaLibrary, networkManager).runAllMigrations()

    setupShutdownHooks(downloadManager)
    setupAutoscraper(mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)

    downloadManager.restoreQueue()
}


@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    cleanupDownloadDirectory()

    val networkManager = NetworkManager()
    val urlDecoder: UrlDecoder = UrlDecoderImpl(networkManager)
    val videoStorage = FileSystemVideoStorage()
    val metadataStorage = CachingMetadataStorage(FileSystemMetadataStorage())
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