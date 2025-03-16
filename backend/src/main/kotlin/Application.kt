package io.sebi

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.sebi.api.api
import io.sebi.autoscrape.setupAutoscraper
import io.sebi.downloader.DownloadManager
import io.sebi.downloader.DownloadManagerImpl
import io.sebi.downloader.IntoMediaLibraryDownloader
import io.sebi.ffmpeg.globalFfmpegMutex
import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.file
import io.sebi.library.id
import io.sebi.network.NetworkManager
import io.sebi.phash.ensureDHashes
import io.sebi.setup.cleanupDownloadDirectory
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.storage.FileSystemVideoStorage
import io.sebi.storage.JdbcSqliteMetadataStorage
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.CachingTagger
import io.sebi.tagging.Tagger
import io.sebi.ui.*
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.UrlDecoderImpl
import io.sebi.utils.ReaderWriterLock
import io.sebi.utils.creationTime
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) {
    // Switch to CIO last blocked by https://youtrack.jetbrains.com/issue/KTOR-6851.
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

    runBlocking {
        downloadManager.restoreQueue()
    }
    setFileDates(metadataStorage)
}

fun setFileDates(metadataStorage: MetadataStorage) {
    val logger = LoggerFactory.getLogger("File Dates")
    runBlocking {
        val totalUpdated = metadataStorage.listAllMetadata().count {
            val creationDate = it.creationDate
            if (creationDate == 0L) {
                val file = it.file
                val unixEpoch = file.creationTime.toMillis() / 1000
                metadataStorage.storeMetadata(
                    it.id,
                    it.copy(creationDate = unixEpoch)
                )
                logger.warn("Updated timestamp for ${it.id} on startup.")
                return@count true
            }
            false
        }
        if (totalUpdated > 0) {
            logger.warn("Updated $totalUpdated file timestamps.")
        }
    }
}


@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    cleanupDownloadDirectory()

    val networkManager = NetworkManager()
    val urlDecoder: UrlDecoder = UrlDecoderImpl(networkManager)
    val videoStorage = FileSystemVideoStorage()

    // Create a shared ReaderWriterLock for SQLite database access
    val sqliteReaderWriterLock = ReaderWriterLock()
    val metadataStorage: MetadataStorage = JdbcSqliteMetadataStorage(sqliteReaderWriterLock)
    runBlocking { removeFilesScheduledForDeletion(metadataStorage, videoStorage) }
    val mediaLibrary = MediaLibrary(urlDecoder, videoStorage, metadataStorage)
    val downloadManager: DownloadManager =
        DownloadManagerImpl(
            metadataStorage,
            urlDecoder,
            networkManager,
            mediaLibrary::addCompletedDownload,
            monitor
        )
    val tagger = CachingTagger()
    val intoMediaLibraryDownloader = IntoMediaLibraryDownloader(downloadManager, urlDecoder, mediaLibrary)

    launch(Dispatchers.IO) {
        delay(45.minutes)
        ensureDHashes(mediaLibrary)
    }

    runBlocking {
        setup(metadataStorage, mediaLibrary, downloadManager, networkManager, tagger, urlDecoder)
    }

//    launch(Dispatchers.Default) {
//        delay(50.minutes)
//        duplicateCalculator.calculateDuplicates()
//    }

    launch(Dispatchers.IO) {
        delay(3.hours)
        generateThumbnails(mediaLibrary)
    }

    installPlugins()

    routing {
        api(urlDecoder, mediaLibrary, downloadManager, tagger, metadataStorage)
        addDownload(intoMediaLibraryDownloader)
        addUpload(mediaLibrary)
        decryptEndpointRoute(urlDecoder)
        progressList(downloadManager)
        videoPlayer(mediaLibrary)
        setupStaticPaths()
    }

    launch {
        warmApi(environment, this)
    }
}

fun warmApi(env: ApplicationEnvironment, scope: CoroutineScope) {
//    embeddedServer.monitor.subscribe(ServerReady) {
//        val conns = env.connectors.first()
//        val url = "http://${conns.host}:${conns.port}/api/mediaLibrary"
//        scope.launch {
//            HttpClient().get(url)
//        }
//    }
}

fun Routing.setupStaticPaths() {
    staticResources("/static", "static")
    staticResources("/", "frontend")
}

private suspend fun generateThumbnails(mediaLibrary: MediaLibrary) {
    val logger = LoggerFactory.getLogger("Thumbnail Generation")
    logger.info("starting thumbnail generation")
    mediaLibrary.getEntries().forEach { mediaLibraryEntry: MediaLibraryEntry ->
        createThumbnails(mediaLibraryEntry, logger)
    }
}

// todo: you should be an ffmpeg task.
private suspend fun createThumbnails(mediaLibraryEntry: MediaLibraryEntry, logger: Logger) {
    globalFfmpegMutex.withLock {
        mediaLibraryEntry.file?.let {
            logger.info("Generating thumbnail for ${mediaLibraryEntry.id}")
            if (it.parentFile?.list()?.none { it.startsWith("thumb") } == true) {
                val proc = ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i",
                    it.name,
                    "-q:v",
                    "5",
                    "-vf",
                    "fps=1/10",
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
