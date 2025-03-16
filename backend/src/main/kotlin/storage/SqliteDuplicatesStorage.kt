package io.sebi.storage

import io.sebi.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite implementation of the DuplicatesStorage interface.
 * Uses the same database as JdbcSqliteMetadataStorage.
 */
class SqliteDuplicatesStorage : DuplicatesStorage {
    private val logger = LoggerFactory.getLogger(SqliteDuplicatesStorage::class.java)
    private val mutex = Mutex()
    private val roomEmpty = Mutex()
    private var readers = 0

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
            // Create duplicates table if it doesn't exist
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS duplicates (
                    src_id TEXT NOT NULL,
                    dup_id TEXT NOT NULL,
                    distance INTEGER NOT NULL
                )
                """
            )

            // Create index for faster lookups
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_duplicates_src_id 
                ON duplicates(src_id)
                """
            )

            // Create unique constraint for src_id and dup_id
            stmt.execute(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS unq_src_dup_ids 
                ON duplicates(src_id, dup_id)
                """
            )
        }
    }

    // Reader/Writer lock implementation
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

    override suspend fun createDuplicate(sourceId: String, destinationId: String, distance: Int) {
        withContext(Dispatchers.IO) {
            roomEmpty.withLock {
                connection.prepareStatement(
                    """
                    INSERT INTO duplicates (src_id, dup_id, distance)
                    VALUES (?, ?, ?) ON CONFLICT (src_id, dup_id) DO NOTHING
                    """
                ).use { stmt ->
                    stmt.setString(1, sourceId)
                    stmt.setString(2, destinationId)
                    stmt.setLong(3, distance.toLong())
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun getDuplicateBySourceId(sourceId: String): Duplicates? {
        return withContext(Dispatchers.IO) {
            withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT * FROM duplicates
                    WHERE src_id = ?
                    ORDER BY distance ASC
                    LIMIT 1
                    """
                ).use { stmt ->
                    stmt.setString(1, sourceId)
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

    override suspend fun getAllDuplicatesForSourceId(sourceId: String): List<Duplicates> {
        return withContext(Dispatchers.IO) {
            withReaderLock {
                connection.prepareStatement(
                    """
                    SELECT * FROM duplicates
                    WHERE src_id = ?
                    ORDER BY distance ASC
                    """
                ).use { stmt ->
                    stmt.setString(1, sourceId)
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

    override suspend fun updateDuplicate(sourceId: String, destinationId: String, distance: Int): Boolean {
        return withContext(Dispatchers.IO) {
            roomEmpty.withLock {
                connection.prepareStatement(
                    """
                    UPDATE duplicates
                    SET distance = ?
                    WHERE src_id = ? AND dup_id = ?
                    """
                ).use { stmt ->
                    stmt.setLong(1, distance.toLong())
                    stmt.setString(2, sourceId)
                    stmt.setString(3, destinationId)
                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun deleteDuplicate(sourceId: String, destinationId: String): Boolean {
        return withContext(Dispatchers.IO) {
            roomEmpty.withLock {
                connection.prepareStatement(
                    """
                    DELETE FROM duplicates
                    WHERE src_id = ? AND dup_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, sourceId)
                    stmt.setString(2, destinationId)
                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun deleteAllDuplicatesForSourceId(sourceId: String): Int {
        return withContext(Dispatchers.IO) {
            roomEmpty.withLock {
                connection.prepareStatement(
                    """
                    DELETE FROM duplicates
                    WHERE src_id = ?
                    """
                ).use { stmt ->
                    stmt.setString(1, sourceId)
                    stmt.executeUpdate()
                }
            }
        }
    }
}