package io.sebi.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.sebi.database.MediaDatabase
import io.sebi.library.MediaLibraryEntry
import io.sebi.sqldelight.mediametadata.Duplicates
import io.sebi.sqldelight.mediametadata.SelectAllWithTags
import io.sebi.sqldelight.mediametadata.SelectById
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val driver = JdbcSqliteDriver("jdbc:sqlite:mediaLibrary/db.sqlite", sqliteConfig.toProperties()).also { driver ->
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
        roomEmpty.withLock {
            database.mediaMetadataQueries.insertOrReplaceEntry(
                unique_id = id,
                title = metadata.name,
                origin_url = metadata.originUrl,
                hits = metadata.hits.toLong(),
                marked_for_deletion = if (metadata.markedForDeletion) 1 else 0,
                creation_date = metadata.creationDate
            )
            metadata.tags.forEach {
                database.tagsQueries.addTag(it) // TODO: This does a lot of `ON CONFLICT DO NOTHING`. Maybe there's a nicer way?
                database.tagsQueries.addTagForLibraryEntryByName(id, it)
            }
        }
    }

    override fun addDuplicate(id: String, dup: String, dist: Int) {
        database.duplicatesQueries.addDuplicate(id, dup, dist.toLong())
    }

    override fun getDuplicate(id: String): Duplicates? {
        val foo = database.duplicatesQueries.selectDuplicateForId(id).executeAsOneOrNull()
        return foo
    }

    override suspend fun retrieveMetadata(id: String): MetadataResult {
        withReaderLock {
            val metadataForId = database.mediaMetadataQueries.selectById(id).executeAsOneOrNull()
            if (metadataForId != null) return MetadataResult.Just(metadataForId.toMediaLibraryEntry())

            val tombstoneForId = database.mediaMetadataQueries.getTombstoneForId(id).executeAsOneOrNull()
            if (tombstoneForId != null) return MetadataResult.Tombstone
            return MetadataResult.None
        }
    }

    override fun deleteMetadata(id: String) {
        database.mediaMetadataQueries.deleteById(id)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun listAllMetadata(): List<MediaLibraryEntry> {
        val (mediaMetadata, time) = measureTimedValue {
            database.mediaMetadataQueries.selectAllWithTags().executeAsList()
        }
        val (entries, entriesTime) = measureTimedValue {
            mediaMetadata.map {
                it.toMediaLibraryEntry()
            }
        }
        logger.info("Selected all metadata in $time, converted to entries in $entriesTime (total ${time + entriesTime})")
        return entries
    }

    fun SelectAllWithTags.toMediaLibraryEntry(): MediaLibraryEntry {
        return MediaLibraryEntry(
            title,
            origin_url,
            Json.decodeFromString(tags),
            creation_date,
            null,
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
            null,
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

