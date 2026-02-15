package io.sebi.storage

import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.utils.ReaderWriterLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class JdbcSqliteMetadataStorage(
    private val readerWriterLock: ReaderWriterLock,
) : MetadataStorage {
    private val logger = LoggerFactory.getLogger(JdbcSqliteMetadataStorage::class.java)

    private val sqliteConfig = SQLiteConfig().apply {
        enforceForeignKeys(true)
    }

    private val connection: Connection by lazy {
        DriverManager.getConnection(AppConfig.databaseUrl, sqliteConfig.toProperties()).apply {
            createSchema()
        }
    }

    private fun Connection.createSchema() {
        createStatement().use { stmt ->
            // Check database version
            val version = stmt.executeQuery("PRAGMA user_version").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }

            // Create tables if they don't exist
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS media_library_entries (
                    unique_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    origin_url TEXT NOT NULL,
                    hits INTEGER NOT NULL DEFAULT 0,
                    marked_for_deletion INTEGER NOT NULL DEFAULT 0,
                    creation_date INTEGER NOT NULL DEFAULT 0
                )
            """
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS media_library_tombstones(
                    unique_id TEXT PRIMARY KEY
                )
            """
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS tags(
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE
                )
            """
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS tags_for_library_entries (
                    library_entry_uuid TEXT NOT NULL REFERENCES media_library_entries ON DELETE CASCADE,
                    tag_id INTEGER NOT NULL REFERENCES tags(id)
                )
            """
            )

            stmt.execute(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS unq_uuid_tag_id 
                ON tags_for_library_entries(library_entry_uuid, tag_id)
            """
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS duplicates (
                    src_id TEXT NOT NULL,
                    dup_id TEXT NOT NULL,
                    distance INTEGER NOT NULL
                )
            """
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS media_information (
                    library_entry_uuid TEXT PRIMARY KEY REFERENCES media_library_entries ON DELETE CASCADE,
                    mime_type TEXT,
                    codec_type TEXT,
                    codec_name TEXT,
                    width INTEGER,
                    height INTEGER,
                    bit_rate INTEGER,
                    frame_rate REAL,
                    file_size INTEGER,
                    duration_seconds REAL,
                    probe_status TEXT NOT NULL,
                    probe_error TEXT,
                    last_probe_at INTEGER NOT NULL
                )
            """
            )

            // Update schema version if needed
            if (version < 3) {
                stmt.execute("PRAGMA user_version = 3")
            }
        }
    }

    private fun ResultSet.longOrNull(column: String): Long? {
        return (getObject(column) as? Number)?.toLong()
    }

    private fun ResultSet.intOrNull(column: String): Int? {
        return (getObject(column) as? Number)?.toInt()
    }

    private fun ResultSet.doubleOrNull(column: String): Double? {
        return (getObject(column) as? Number)?.toDouble()
    }


    override suspend fun storeMetadata(id: String, metadata: MediaLibraryEntry) {
        withContext(Dispatchers.IO) {
            readerWriterLock.withWriterLock {
                connection.prepareStatement(
                    """
                    INSERT OR REPLACE INTO media_library_entries(
                        unique_id, title, origin_url, hits, marked_for_deletion, creation_date
                    ) VALUES (?, ?, ?, ?, ?, ?)
                """
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, metadata.name)
                    stmt.setString(3, metadata.originUrl)
                    stmt.setInt(4, metadata.hits)
                    stmt.setInt(5, if (metadata.markedForDeletion) 1 else 0)
                    stmt.setLong(6, metadata.creationDate)
                    stmt.executeUpdate()
                }

                connection.prepareStatement("INSERT INTO tags (name) VALUES (?) ON CONFLICT DO NOTHING").use { stmt ->
                    metadata.tags.forEach { tag ->
                        stmt.setString(1, tag)
                        stmt.executeUpdate()
                    }
                }

                connection.prepareStatement(
                    """
                    INSERT OR IGNORE INTO tags_for_library_entries (library_entry_uuid, tag_id)
                    SELECT ?, tags.id FROM tags WHERE name = ?
                """
                ).use { stmt ->
                    metadata.tags.forEach { tag ->
                        stmt.setString(1, id)
                        stmt.setString(2, tag)
                        stmt.executeUpdate()
                    }
                }
            }
        }
    }

    override suspend fun retrieveMetadata(id: String): MetadataResult {
        return withContext(Dispatchers.IO) {
            readerWriterLock.withReaderLock {
                // Check for tombstone first
                connection.prepareStatement("SELECT 1 FROM media_library_tombstones WHERE unique_id = ?").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return@withReaderLock MetadataResult.Tombstone
                        }
                    }
                }

                // If not tombstone, try to get the entry with tags
                connection.prepareStatement(
                    """
                    SELECT e.*, CASE 
                        WHEN COUNT(t.name) = 0 THEN '[]'
                        ELSE json_group_array(t.name)
                    END as tags
                    FROM media_library_entries e
                    LEFT JOIN tags_for_library_entries tfle ON e.unique_id = tfle.library_entry_uuid
                    LEFT JOIN tags t ON t.id = tfle.tag_id
                    WHERE e.unique_id = ?
                    GROUP BY e.unique_id
                """
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val entry = MediaLibraryEntry(
                                name = rs.getString("title"),
                                originUrl = rs.getString("origin_url"),
                                tags = Json.decodeFromString(rs.getString("tags")),
                                creationDate = rs.getLong("creation_date"),
                                uid = rs.getString("unique_id"),
                                hits = rs.getInt("hits"),
                                markedForDeletion = rs.getInt("marked_for_deletion") == 1
                            )
                            return@withReaderLock MetadataResult.Just(entry)
                        }
                    }
                }
                return@withReaderLock MetadataResult.None
            }
        }
    }

    override suspend fun deleteMetadata(id: String) {
        withContext(Dispatchers.IO) {
            readerWriterLock.withWriterLock {
                connection.prepareStatement("DELETE FROM media_library_entries WHERE unique_id = ?").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeUpdate()
                }

                connection.prepareStatement("INSERT INTO media_library_tombstones (unique_id) VALUES (?)").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun listAllMetadata(): List<MediaLibraryEntry> {
        return withContext(Dispatchers.IO) {
            readerWriterLock.withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT e.*, CASE 
                        WHEN COUNT(t.name) = 0 THEN '[]'
                        ELSE json_group_array(t.name)
                    END as tags
                    FROM media_library_entries e
                    LEFT JOIN tags_for_library_entries tfle ON e.unique_id = tfle.library_entry_uuid
                    LEFT JOIN tags t ON t.id = tfle.tag_id
                    GROUP BY e.unique_id
                    ORDER BY e.creation_date DESC
                """
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<MediaLibraryEntry>()
                        while (rs.next()) {
                            results.add(
                                MediaLibraryEntry(
                                    name = rs.getString("title"),
                                    originUrl = rs.getString("origin_url"),
                                    tags = Json.decodeFromString(rs.getString("tags")),
                                    creationDate = rs.getLong("creation_date"),
                                    uid = rs.getString("unique_id"),
                                    hits = rs.getInt("hits"),
                                    markedForDeletion = rs.getInt("marked_for_deletion") == 1
                                )
                            )
                        }
                        results
                    }
                }
            }
        }
    }

    override suspend fun listTombstones(): List<String> {
        return withContext(Dispatchers.IO) {
            readerWriterLock.withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT unique_id FROM media_library_tombstones
                """
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<String>()
                        while (rs.next()) {
                            results.add(rs.getString("unique_id"))
                        }
                        results
                    }
                }
            }
        }
    }

    override suspend fun getMediaInformation(id: String): MediaInformation? {
        return withContext(Dispatchers.IO) {
            readerWriterLock.withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT * FROM media_information
                    WHERE library_entry_uuid = ?
                """
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) return@withReaderLock null
                        MediaInformation(
                            libraryEntryId = rs.getString("library_entry_uuid"),
                            mimeType = rs.getString("mime_type"),
                            codecType = rs.getString("codec_type"),
                            codecName = rs.getString("codec_name"),
                            width = rs.intOrNull("width"),
                            height = rs.intOrNull("height"),
                            bitRate = rs.longOrNull("bit_rate"),
                            frameRate = rs.doubleOrNull("frame_rate"),
                            fileSize = rs.longOrNull("file_size"),
                            durationSeconds = rs.doubleOrNull("duration_seconds"),
                            probeStatus = MediaProbeStatus.valueOf(rs.getString("probe_status")),
                            probeError = rs.getString("probe_error"),
                            lastProbeAt = rs.getLong("last_probe_at")
                        )
                    }
                }
            }
        }
    }

    override suspend fun upsertMediaInformation(info: MediaInformation) {
        withContext(Dispatchers.IO) {
            readerWriterLock.withWriterLock {
                connection.prepareStatement(
                    """
                    INSERT OR REPLACE INTO media_information (
                        library_entry_uuid,
                        mime_type,
                        codec_type,
                        codec_name,
                        width,
                        height,
                        bit_rate,
                        frame_rate,
                        file_size,
                        duration_seconds,
                        probe_status,
                        probe_error,
                        last_probe_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                ).use { stmt ->
                    stmt.setString(1, info.libraryEntryId)
                    stmt.setString(2, info.mimeType)
                    stmt.setString(3, info.codecType)
                    stmt.setString(4, info.codecName)
                    stmt.setObject(5, info.width)
                    stmt.setObject(6, info.height)
                    stmt.setObject(7, info.bitRate)
                    stmt.setObject(8, info.frameRate)
                    stmt.setObject(9, info.fileSize)
                    stmt.setObject(10, info.durationSeconds)
                    stmt.setString(11, info.probeStatus.name)
                    stmt.setString(12, info.probeError)
                    stmt.setLong(13, info.lastProbeAt)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun addDuplicate(id: String, dup: String, dist: Int) {
        withContext(Dispatchers.IO) {
            readerWriterLock.withWriterLock {
                connection.prepareStatement(
                    """
                    INSERT INTO duplicates (src_id, dup_id, distance)
                    VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, dup)
                    stmt.setLong(3, dist.toLong())
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun getDuplicate(id: String): Duplicates? {
        return withContext(Dispatchers.IO) {
            readerWriterLock.withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT * FROM duplicates
                    WHERE src_id = ?
                    ORDER BY distance ASC
                    LIMIT 1
                """
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            Duplicates(
                                src_id = rs.getString("src_id"),
                                dup_id = rs.getString("dup_id"),
                                distance = rs.getLong("distance")
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun getTopDuplicates(limit: Int): List<Duplicates> {
        return withContext(Dispatchers.IO) {
            readerWriterLock.withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT * FROM duplicates
                    ORDER BY distance ASC
                    LIMIT ?
                    """
                ).use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<Duplicates>()
                        while (rs.next()) {
                            results.add(
                                Duplicates(
                                    src_id = rs.getString("src_id"),
                                    dup_id = rs.getString("dup_id"),
                                    distance = rs.getLong("distance")
                                )
                            )
                        }
                        results
                    }
                }
            }
        }
    }
}
