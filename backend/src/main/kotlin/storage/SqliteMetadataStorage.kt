package io.sebi.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.sebi.config.AppConfig
import io.sebi.database.MediaDatabase
import io.sebi.library.MediaLibraryEntry
import io.sebi.sqldelight.mediametadata.Duplicates
import io.sebi.sqldelight.mediametadata.SelectAllWithTags
import io.sebi.sqldelight.mediametadata.SelectById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class SqliteMetadataStorage : MetadataStorage {
    val logger = LoggerFactory.getLogger("Sqlite Metadata Storage")
    val sqliteConfig = SQLiteConfig().apply {
        enforceForeignKeys(true)
    }
    val driver = JdbcSqliteDriver(AppConfig.databaseUrl, sqliteConfig.toProperties()).also { driver ->
        fun getVersion(): Int {
            return driver.executeQuery(null, "PRAGMA user_version;", {
                app.cash.sqldelight.db.QueryResult.Value(it.getLong(0)!!.toInt())
            }, 0, null).value
        }

        fun setVersion(version: Int) {
            driver.execute(null, String.format("PRAGMA user_version = %d;", version), 0, null)
        }
        if (getVersion() == 0) {
            MediaDatabase.Schema.create(driver)
            setVersion(1) // TODO: Un-hardcode this
        }

    } // TODO: Database & Driver are supposed to be singletons as per docs (https://cashapp.github.io/sqldelight/jvm_sqlite/)

    // TODO: This should also not live in apply block, holy moly
    val database = MediaDatabase(driver)

    var readers = 0
    val mutex = Mutex()
    val roomEmpty = Mutex()

    // https://github.com/Kotlin/kotlinx.coroutines/issues/94
    // https://greenteapress.com/semaphores/LittleBookOfSemaphores.pdf Ch 4.2
    private suspend inline fun <T> withReaderLock(criticalSection: () -> T): T {
        mutex.withLock {
            readers++
            if (readers == 1)
                roomEmpty.lock()
        }
        try {
            return criticalSection()
        } finally {
            mutex.withLock {
                readers--
                if (readers == 0) {
                    roomEmpty.unlock()
                }
            }
        }
    }

    override suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        // todo: this writer lock should probably be everywhere.
        withContext(Dispatchers.IO) {
            roomEmpty.withLock {
                database.mediaMetadataQueries.insertOrReplaceEntry(
                    unique_id = id,
                    title = metadata.name,
                    origin_url = metadata.originUrl,
                    hits = metadata.hits.toLong(),
                    marked_for_deletion = if (metadata.markedForDeletion) 1 else 0,
                    creation_date = metadata.creationDate
                )
                // https://slack-chats.kotlinlang.org/t/497361/with-sqldelight-is-there-any-way-for-a-bulk-insert-beyond-a-
                database.tagsQueries.transaction {
                    metadata.tags.forEach {
                        database.tagsQueries.addTag(it) // TODO: This does a lot of `ON CONFLICT DO NOTHING`. Maybe there's a nicer way?
                        database.tagsQueries.addTagForLibraryEntryByName(id, it)
                    }
                }
            }
        }
    }

    override suspend fun addDuplicate(id: String, dup: String, dist: Int) {
        withContext(Dispatchers.IO) {
            database.duplicatesQueries.addDuplicate(id, dup, dist.toLong())
        }
    }

    override suspend fun getDuplicate(id: String): Duplicates? {
        return withContext(Dispatchers.IO) {
            database.duplicatesQueries.selectDuplicateForId(id).executeAsOneOrNull()
        }
    }

    override suspend fun retrieveMetadata(id: String): MetadataResult {
        val result = withContext(Dispatchers.IO) {
            withReaderLock {
                val metadataForId = database.mediaMetadataQueries.selectById(id).executeAsOneOrNull()
                if (metadataForId != null) return@withContext MetadataResult.Just(metadataForId.toMediaLibraryEntry())

                val tombstoneForId = database.mediaMetadataQueries.getTombstoneForId(id).executeAsOneOrNull()
                if (tombstoneForId != null) return@withContext MetadataResult.Tombstone
                return@withContext MetadataResult.None
            }
        }
        return result
    }

    override suspend fun deleteMetadata(id: String) {
        withContext(Dispatchers.IO) {
            database.mediaMetadataQueries.deleteById(id)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun listAllMetadata(): List<MediaLibraryEntry> {
        val list = withContext(Dispatchers.IO) {
            val (mediaMetadata, time) = measureTimedValue {
                database.mediaMetadataQueries.selectAllWithTags().executeAsList()
            }
            val (entries, entriesTime) = measureTimedValue {
                mediaMetadata.map {
                    ensureActive()
                    it.toMediaLibraryEntry()
                }
            }
            logger.info("Selected all metadata in $time, converted to entries in $entriesTime (total ${time + entriesTime})")
            entries
        }
        return list
    }

    fun SelectAllWithTags.toMediaLibraryEntry(): MediaLibraryEntry {
        return MediaLibraryEntry(
            title,
            origin_url,
            Json.decodeFromString(tags),
            creation_date,
            unique_id,
            hits.toInt(),
            marked_for_deletion.asSqlBoolean()
        )
    }

    fun SelectById.toMediaLibraryEntry(): MediaLibraryEntry {
        return MediaLibraryEntry(
            title,
            origin_url,
            Json.decodeFromString(tags),
            creation_date,
            unique_id,
            hits.toInt(),
            marked_for_deletion.asSqlBoolean()
        )
    }

    fun Long.asSqlBoolean(): Boolean {
        return when (this) {
            0L -> false
            1L -> true
            else -> error("SQL boolean should be 0 or 1, but got $this instead.")
        }
    }
}
