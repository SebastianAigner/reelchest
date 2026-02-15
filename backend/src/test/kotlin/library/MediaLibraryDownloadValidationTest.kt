package io.sebi.library

import io.sebi.config.AppConfig
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.storage.FileSystemMetadataStorage
import io.sebi.storage.FileSystemVideoStorage
import io.sebi.urldecoder.DecryptResponse
import io.sebi.urldecoder.MetadataResponse
import io.sebi.urldecoder.UrlDecoder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaLibraryDownloadValidationTest {
    private class StubUrlDecoder : UrlDecoder {
        override suspend fun decodeUrl(url: String): DecryptResponse? = null
        override suspend fun getMetadata(url: String): MetadataResponse? = null
    }

    @Test
    fun addCompletedDownloadAcceptsValidVideo() = withTempPaths { baseDir ->
        val mediaLibrary = MediaLibrary(StubUrlDecoder(), FileSystemVideoStorage(), FileSystemMetadataStorage())
        val originUrl = "https://example.com/valid-video"
        val sourceFile = File("src/test/resources/ForBiggerJoyrides.mp4")
        val downloadedFile = File(baseDir, "valid-video.mp4")
        sourceFile.copyTo(downloadedFile, overwrite = true)

        runBlocking {
            mediaLibrary.addCompletedDownload(CompletedDownloadTask(downloadedFile, originUrl))
        }

        val uid = originUrl.shaHashed()
        val storedFile = File(AppConfig.mediaLibraryPath, "$uid/$uid.mp4")
        val storedMetadata = File(AppConfig.mediaLibraryPath, "$uid/$uid.json")
        assertTrue(storedFile.exists(), "Validated downloads should be stored in the media library.")
        assertTrue(storedMetadata.exists(), "Metadata should be persisted for completed downloads.")
        assertFalse(downloadedFile.exists(), "Downloaded temp file should be deleted after storage.")
    }

    @Test
    fun addCompletedDownloadRejectsNonVideo() = withTempPaths { baseDir ->
        val mediaLibrary = MediaLibrary(StubUrlDecoder(), FileSystemVideoStorage(), FileSystemMetadataStorage())
        val originUrl = "https://example.com/bad-download"
        val downloadedFile = File(baseDir, "bad-download.html").apply {
            writeText(
                """
                <html>
                <head><title>502 Bad Gateway</title></head>
                <body>
                <center><h1>502 Bad Gateway</h1></center>
                </body>
                </html>
                """.trimIndent()
            )
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                mediaLibrary.addCompletedDownload(CompletedDownloadTask(downloadedFile, originUrl))
            }
        }

        val uid = originUrl.shaHashed()
        val storedFile = File(AppConfig.mediaLibraryPath, "$uid/$uid.mp4")
        assertFalse(storedFile.exists(), "Rejected downloads should not be stored in the media library.")
        assertFalse(downloadedFile.exists(), "Rejected download artifacts should be removed.")
    }

    private fun withTempPaths(block: (File) -> Unit) {
        val baseDir = Files.createTempDirectory("media-library-download-test").toFile()
        try {
            val userConfig = File(baseDir, "userConfig").absolutePath
            val mediaLibrary = File(baseDir, "mediaLibrary").absolutePath
            val database = File(baseDir, "database/db.sqlite").absolutePath
            AppConfig.withPaths(
                userConfig = userConfig,
                mediaLibrary = mediaLibrary,
                database = database
            ) {
                AppConfig.ensureDirectories()
                block(baseDir)
            }
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
