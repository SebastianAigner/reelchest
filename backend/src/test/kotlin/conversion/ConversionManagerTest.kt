package io.sebi.conversion

import io.ktor.events.*
import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.storage.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConversionManagerTest {
    @Test
    fun validateOptionsRejectsInvalidValues() {
        val manager = ConversionManager(NoopMetadataStorage(), NoopVideoStorage(), Events())
        manager.validateOptions(
            ConversionOptions(
                maxWidth = 1920,
                maxHeight = 1080,
                fps = 30,
                crf = 21,
                audioBitrateKbps = 160
            )
        )

        assertFailsWith<IllegalStateException> {
            manager.validateOptions(ConversionOptions(crf = 29))
        }
        assertFailsWith<IllegalStateException> {
            manager.validateOptions(ConversionOptions(fps = 0))
        }
        assertFailsWith<IllegalStateException> {
            manager.validateOptions(ConversionOptions(maxWidth = 1920, maxHeight = null))
        }
        assertFailsWith<IllegalStateException> {
            manager.validateOptions(ConversionOptions(audioBitrateKbps = 0))
        }
    }

    @Test
    fun restoreQueueRequeuesRunningTask() = runBlocking {
        val tempDir = Files.createTempDirectory("reelchest-conversion-test").toFile()
        val userConfigDir = File(tempDir, "userConfig")
        val mediaLibraryDir = File(tempDir, "mediaLibrary")
        val databaseFile = File(tempDir, "database/db.sqlite")
        userConfigDir.mkdirs()
        mediaLibraryDir.mkdirs()
        databaseFile.parentFile.mkdirs()

        AppConfig.withPaths(
            userConfig = userConfigDir.absolutePath,
            mediaLibrary = mediaLibraryDir.absolutePath,
            database = databaseFile.absolutePath
        ) {
            runBlocking {
                val manager = ConversionManager(NoopMetadataStorage(), NoopVideoStorage(), Events())
                manager.shutdown()
                val queueFile = File(AppConfig.userConfigPath, "conversions.json")
                queueFile.writeText(
                    """
                    {
                      "immediate": [],
                      "scheduled": [],
                      "running": {
                        "id": "task-1",
                        "sourceId": "src-1",
                        "sourceTitle": "Test Video",
                        "mode": "IMMEDIATE",
                        "options": {"maxWidth": 1920, "maxHeight": 1080, "fps": 30, "crf": 21, "audioBitrateKbps": 160},
                        "createdAt": 123456
                      },
                      "problematic": []
                    }
                    """.trimIndent()
                )
                manager.restoreQueue()

                val snapshot = manager.snapshot()
                assertEquals(1, snapshot.queued.size)
                assertNull(snapshot.running)
            }
        }
    }
}

private class NoopMetadataStorage : MetadataStorage {
    override suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry) = Unit
    override suspend fun retrieveMetadata(id: String): MetadataResult = MetadataResult.None
    override suspend fun deleteMetadata(id: String) = Unit
    override suspend fun listAllMetadata(): List<MediaLibraryEntry> = emptyList()
    override suspend fun listTombstones(): List<String> = emptyList()
    override suspend fun getMediaInformation(id: String): MediaInformation? = null
    override suspend fun upsertMediaInformation(info: MediaInformation) = Unit
    override suspend fun searchMediaInformation(
        sort: MediaInfoSort,
        order: SortOrder,
        limit: Int,
    ): List<MediaInformationSummary> = emptyList()
    override suspend fun addDuplicate(id: String, dup: String, dist: Int) = Unit
    override suspend fun getDuplicate(id: String): Duplicates? = null
    override suspend fun getTopDuplicates(limit: Int): List<Duplicates> = emptyList()
}

private class NoopVideoStorage : VideoStorage {
    override fun storeVideo(id: String, videoFile: java.nio.file.Path) = Unit
    override fun deleteVideo(id: String) = Unit
}
