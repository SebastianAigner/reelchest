package io.sebi

import io.ktor.server.application.*
import io.ktor.server.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.sebi.api.api
import io.sebi.api.handlers.triggerOrphanScan
import io.sebi.autoscrape.setupAutoscraper
import io.sebi.conversion.ConversionManager
import io.sebi.disk.MediaLibraryDiskSpaceProvider
import io.sebi.downloader.DiskSpaceDownloadStartGate
import io.sebi.downloader.DownloadManager
import io.sebi.downloader.DownloadManagerImpl
import io.sebi.downloader.IntoMediaLibraryDownloader
import io.sebi.ffmpeg.generateThumbnails
import io.sebi.heavymutex.HeavyMutex
import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.file
import io.sebi.library.id
import io.sebi.network.NetworkManager
import io.sebi.phash.ensureDHashes
import io.sebi.scheduler.scheduleDailyAt
import io.sebi.setup.cleanupDownloadDirectory
import io.sebi.setup.removeFilesScheduledForDeletion
import io.sebi.storage.FileSystemVideoStorage
import io.sebi.storage.JdbcSqliteMetadataStorage
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.CachingTagger
import io.sebi.tagging.Tagger
import io.sebi.ui.addDownload
import io.sebi.ui.addUpload
import io.sebi.ui.progressList
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.UrlDecoderImpl
import io.sebi.utils.ReaderWriterLock
import io.sebi.utils.creationTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalTime

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
    conversionManager: ConversionManager,
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
        conversionManager.restoreQueue()
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
    AppLifecycle.startedAt
    val application = this
    cleanupDownloadDirectory()

    val networkManager = NetworkManager()
    val urlDecoder: UrlDecoder = UrlDecoderImpl(networkManager)
    val videoStorage = FileSystemVideoStorage()

    // Create a shared ReaderWriterLock for SQLite database access
    val sqliteReaderWriterLock = ReaderWriterLock()
    val metadataStorage: MetadataStorage = JdbcSqliteMetadataStorage(sqliteReaderWriterLock)
    runBlocking {
        val summary = removeFilesScheduledForDeletion(metadataStorage, videoStorage)
        if (summary.totalMarked > 0) {
            LoggerFactory.getLogger("Scheduler").info(
                "Startup deletion removed ${summary.deleted} of ${summary.totalMarked} marked entries " +
                        "(${summary.failed} failed)."
            )
        }
    }
    val mediaLibrary = MediaLibrary(urlDecoder, videoStorage, metadataStorage)
    val downloadManager: DownloadManager =
        DownloadManagerImpl(
            metadataStorage,
            urlDecoder,
            networkManager,
            mediaLibrary::addCompletedDownload,
            DiskSpaceDownloadStartGate(MediaLibraryDiskSpaceProvider()),
            monitor
        )
    val conversionManager = ConversionManager(metadataStorage, videoStorage, monitor)
    val tagger = CachingTagger()
    val intoMediaLibraryDownloader = IntoMediaLibraryDownloader(downloadManager, urlDecoder, mediaLibrary)

    runBlocking {
        setup(metadataStorage, mediaLibrary, downloadManager, networkManager, tagger, urlDecoder, conversionManager)
    }

//    launch(Dispatchers.Default) {
//        delay(50.minutes)
//        duplicateCalculator.calculateDuplicates()
//    }

    // Create a mutex for thumbnail generation to prevent overlapping processes
    val thumbnailGenerationMutex = HeavyMutex("ThumbnailGenerationMutex")

    val schedulerLogger = LoggerFactory.getLogger("Scheduler")
    scheduleDailyAt(
        time = LocalTime.of(2, 0),
        dispatcher = Dispatchers.IO,
        onError = { schedulerLogger.warn("Scheduled thumbnail generation failed.", it) },
        task = {
            schedulerLogger.info("Starting scheduled thumbnail generation.")
            thumbnailGenerationMutex.withLock("Scheduled thumbnail generation") {
                generateThumbnails(mediaLibrary)
            }
        }
    )
    scheduleDailyAt(
        time = LocalTime.of(3, 0),
        dispatcher = Dispatchers.IO,
        onError = { schedulerLogger.warn("Scheduled dhash generation failed.", it) },
        task = {
            schedulerLogger.info("Starting scheduled dhash generation.")
            ensureDHashes(mediaLibrary)
        }
    )
    scheduleDailyAt(
        time = LocalTime.of(3, 0),
        dispatcher = Dispatchers.IO,
        onError = { schedulerLogger.warn("Scheduled orphan scan failed.", it) },
        task = {
            schedulerLogger.info("Starting scheduled orphaned media cleanup.")
            triggerOrphanScan(metadataStorage, application)
        }
    )
    scheduleDailyAt(
        time = LocalTime.of(3, 0),
        dispatcher = Dispatchers.IO,
        onError = { schedulerLogger.warn("Scheduled conversions failed.", it) },
        task = {
            schedulerLogger.info("Starting scheduled conversions.")
            conversionManager.runScheduledConversions()
        }
    )
    scheduleDailyAt(
        time = LocalTime.of(4, 0),
        dispatcher = Dispatchers.IO,
        onError = { schedulerLogger.warn("Scheduled deletion of marked media failed.", it) },
        task = {
            schedulerLogger.info("Starting scheduled deletion of files marked for deletion.")
            val summary = removeFilesScheduledForDeletion(metadataStorage, videoStorage)
            schedulerLogger.info(
                "Scheduled deletion removed ${summary.deleted} of ${summary.totalMarked} marked entries " +
                        "(${summary.failed} failed)."
            )
        }
    )

    installPlugins()

    routing {
        install(HttpRequestLifecycle) {
            cancelCallOnClose = true
        }
        api(urlDecoder, mediaLibrary, downloadManager, tagger, metadataStorage, videoStorage, conversionManager)
        addDownload(intoMediaLibraryDownloader)
        addUpload()
        progressList(downloadManager)
        setupStaticPaths()
    }
}

fun Routing.setupStaticPaths() {
    staticResources("/static", "static")
    staticResources("/", "frontend")
}

/**
 * Generates thumbnails for all media library entries.
 * This function is called periodically (once per hour) and is protected by a mutex
 * to prevent overlapping invocations, as thumbnail generation can take a long time.
 */
private suspend fun generateThumbnails(mediaLibrary: MediaLibrary) {
    val logger = LoggerFactory.getLogger("Thumbnail Generation")
    logger.info("Starting thumbnail generation")
    mediaLibrary.getEntries().forEach { mediaLibraryEntry: MediaLibraryEntry ->
        createThumbnails(mediaLibraryEntry, logger)
    }
    logger.info("Thumbnail generation completed")
}

/**
 * Creates thumbnails for a specific media library entry.
 * This function uses FFmpeg to generate thumbnails and is protected by the globalFfmpegMutex
 * to ensure that only one FFmpeg process runs at a time.
 */
private suspend fun createThumbnails(mediaLibraryEntry: MediaLibraryEntry, logger: Logger) {
    val mediaFile = mediaLibraryEntry.file
    logger.info("Generating thumbnail for ${mediaLibraryEntry.id}")
    if (mediaFile.parentFile?.list()?.none { entry -> entry.startsWith("thumb") } == true) {
        generateThumbnails(mediaFile, logger)
    }
}
