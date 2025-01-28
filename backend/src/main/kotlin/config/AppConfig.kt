package io.sebi.config

import java.io.File

object AppConfig {
    private const val DEFAULT_USER_CONFIG_PATH = "userConfig"
    private const val DEFAULT_MEDIA_LIBRARY_PATH = "mediaLibrary"
    private const val DEFAULT_DATABASE_PATH = "database/db.sqlite"
    private const val DEFAULT_DOWNLOADS_PATH = "downloads"

    private var overrides = emptyMap<String, String>()

    val userConfigPath: String
        get() = overrides["userConfig"] ?: System.getenv("REELCHEST_USER_CONFIG_PATH") ?: DEFAULT_USER_CONFIG_PATH

    val mediaLibraryPath: String
        get() = overrides["mediaLibrary"] ?: System.getenv("REELCHEST_MEDIA_LIBRARY_PATH") ?: DEFAULT_MEDIA_LIBRARY_PATH

    val databasePath: String
        get() = overrides["database"] ?: System.getenv("REELCHEST_DATABASE_PATH") ?: DEFAULT_DATABASE_PATH

    val downloadsPath: String
        get() = overrides["downloads"] ?: System.getenv("REELCHEST_DOWNLOADS_PATH") ?: DEFAULT_DOWNLOADS_PATH

    val databaseUrl: String
        get() = "jdbc:sqlite:$databasePath"

    fun ensureDirectories() {
        File(userConfigPath).mkdirs()
        File(mediaLibraryPath).mkdirs()
        File(databasePath).parentFile?.mkdirs()
        File(downloadsPath).mkdirs()
    }

    fun withPaths(
        userConfig: String? = null,
        mediaLibrary: String? = null,
        database: String? = null,
        block: () -> Unit,
    ) {
        val previousOverrides = overrides
        overrides = buildMap {
            userConfig?.let { put("userConfig", it) }
            mediaLibrary?.let { put("mediaLibrary", it) }
            database?.let { put("database", it) }
        }
        try {
            ensureDirectories()
            block()
        } finally {
            overrides = previousOverrides
            ensureDirectories()
        }
    }

    init {
        ensureDirectories()
    }
}
