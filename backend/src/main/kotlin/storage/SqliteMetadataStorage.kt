package io.sebi.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.sebi.database.MediaDatabase
import io.sebi.library.MediaLibraryEntry
import io.sebi.sqldelight.mediametadata.SelectAllWithTags
import io.sebi.sqldelight.mediametadata.SelectById
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.sqlite.SQLiteConfig

class SqliteMetadataStorage : MetadataStorage {
    val sqliteConfig = SQLiteConfig().apply {
        enforceForeignKeys(true)
    }
    val driver = JdbcSqliteDriver("jdbc:sqlite:mediaLibrary/db.sqlite", sqliteConfig.toProperties()).also { driver ->
        fun getVersion(): Int {
            return driver.executeQuery(null, "PRAGMA user_version;", { it.getLong(0)!!.toInt() }, 0, null)
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


    override fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        database.mediaMetadataQueries.insertOrReplaceEntry(
            id,
            metadata.name,
            metadata.originUrl,
            metadata.hits.toLong(),
            if (metadata.markedForDeletion) 1 else 0
        )
        metadata.tags.forEach {
            database.tagsQueries.addTag(it) // TODO: This does a lot of `ON CONFLICT DO NOTHING`. Maybe there's a nicer way?
            database.tagsQueries.addTagForLibraryEntryByName(id, it)
        }
    }

    override fun retrieveMetadata(id: String): MetadataResult {
        val metadataForId = database.mediaMetadataQueries.selectById(id).executeAsOneOrNull()
        if (metadataForId != null) return MetadataResult.Just(metadataForId.toMediaLibraryEntry())

        val tombstoneForId = database.mediaMetadataQueries.getTombstoneForId(id).executeAsOneOrNull()
        if (tombstoneForId != null) return MetadataResult.Tombstone
        return MetadataResult.None
    }

    override fun deleteMetadata(id: String) {
        database.mediaMetadataQueries.deleteById(id)
    }

    override fun listAllMetadata(): List<MediaLibraryEntry> {
        val mediaMetadata = database.mediaMetadataQueries.selectAllWithTags().executeAsList()
        return mediaMetadata.map {
            it.toMediaLibraryEntry()
        }
    }

    fun SelectAllWithTags.toMediaLibraryEntry(): MediaLibraryEntry {
        return MediaLibraryEntry(title,
            origin_url,
            Json.decodeFromString(tags),
            null,
            unique_id,
            hits.toInt(),
            marked_for_deletion.asSqlBoolean())
    }

    fun SelectById.toMediaLibraryEntry(): MediaLibraryEntry {
        return MediaLibraryEntry(title,
            origin_url,
            Json.decodeFromString(tags),
            null,
            unique_id,
            hits.toInt(),
            marked_for_deletion.asSqlBoolean())
    }

    fun Long.asSqlBoolean(): Boolean {
        return when (this) {
            0L -> false
            1L -> true
            else -> error("SQL boolean should be 0 or 1, but got $this instead.")
        }
    }
}

