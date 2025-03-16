package io.sebi

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.sebi.config.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Base class for tests that need a test server.
 * Provides utility methods for setting up test paths and running a test server.
 */
abstract class WithTestServerTestBase {
    protected fun copyTestDatabase(testDir: String) {
        // Create all necessary directories
        val testDirFile = File(testDir)
        File(testDirFile, "database").mkdirs()
        File(testDirFile, "mediaLibrary").mkdirs()
        File(testDirFile, "userConfig").mkdirs()

        // Get paths relative to original working directory
        val originalWorkingDir = System.getProperty("user.dir")
        val projectRoot = File(originalWorkingDir).parentFile

        // Set up target paths
        val targetDb = File(testDirFile, "database/db.sqlite")
        val targetMediaLib = File(testDirFile, "mediaLibrary")

        // Only copy database if it doesn't exist
        if (!targetDb.exists()) {
            val sourceDb = File(projectRoot, "database/db.sqlite")
            println("[DEBUG_LOG] Source database path: ${sourceDb.absolutePath}")
            println("[DEBUG_LOG] Source database exists: ${sourceDb.exists()}")
            if (sourceDb.exists()) {
                println("[DEBUG_LOG] Source database size: ${sourceDb.length()} bytes")
                sourceDb.copyTo(targetDb)
                println("[DEBUG_LOG] Database copied to: ${targetDb.absolutePath}")
                println("[DEBUG_LOG] Target database size: ${targetDb.length()} bytes")
            }
        } else {
            println("[DEBUG_LOG] Target database already exists, skipping copy")
        }

        // Only copy media library if the directory is empty
        val targetMediaLibFiles = targetMediaLib.listFiles()
        if (targetMediaLibFiles == null || targetMediaLibFiles.isEmpty()) {
            val sourceMediaLib = File(projectRoot, "mediaLibrary")
            println("[DEBUG_LOG] Source media library path: ${sourceMediaLib.absolutePath}")
            println("[DEBUG_LOG] Source media library exists: ${sourceMediaLib.exists()}")

            if (sourceMediaLib.exists() && sourceMediaLib.isDirectory) {
                sourceMediaLib.copyRecursively(targetMediaLib)
                println("[DEBUG_LOG] Media files copied to: ${targetMediaLib.absolutePath}")
                println("[DEBUG_LOG] Media library contents: ${targetMediaLib.listFiles()?.joinToString { it.name }}")
            } else {
                println("[DEBUG_LOG] WARNING: Source media library not found!")
            }
        } else {
            println("[DEBUG_LOG] Target media library not empty, skipping copy")
        }
    }

    protected fun withTestPaths(block: () -> Unit) {
        val originalWorkingDir = System.getProperty("user.dir")
        try {
            // Use a fixed test directory name
            val testDirName = "test_media_library"
            val testDir = File(originalWorkingDir, testDirName).absoluteFile
            testDir.mkdirs()
            println("[DEBUG_LOG] Original working directory: $originalWorkingDir")
            println("[DEBUG_LOG] Test directory: ${testDir.absolutePath}")

            // Copy test database
            copyTestDatabase(testDir.absolutePath)

            // Set working directory to test directory
            System.setProperty("user.dir", testDir.absolutePath)
            println("[DEBUG_LOG] New working directory: ${System.getProperty("user.dir")}")

            val dbPath = File(testDir, "database/db.sqlite").absolutePath
            val mediaLibPath = File(testDir, "mediaLibrary").absolutePath
            val userConfigPath = File(testDir, "userConfig").absolutePath
            println("[DEBUG_LOG] Database path: $dbPath")
            println("[DEBUG_LOG] Media library path: $mediaLibPath")
            println("[DEBUG_LOG] Database exists: ${File(dbPath).exists()}")
            println("[DEBUG_LOG] Media library exists: ${File(mediaLibPath).exists()}")

            AppConfig.withPaths(
                userConfig = userConfigPath,
                mediaLibrary = mediaLibPath,
                database = dbPath
            ) {
                block()
            }
        } finally {
            System.setProperty("user.dir", originalWorkingDir)
        }
    }

    protected fun withTestServer(block: suspend (ApplicationEngine, HttpClient, Int) -> Unit) {
        withTestPaths {
            runBlocking {
                val server = embeddedServer(Netty, port = 0, host = "localhost") {
                    module()
                }.start(wait = false)

                val client = HttpClient(CIO)
                try {
                    // Give the server a moment to be ready
                    delay(500)
                    val port = server.engine.resolvedConnectors().first().port
                    block(server.engine, client, port)
                } finally {
                    client.close()
                    server.stop(1000, 1000)
                }
            }
        }
    }
}