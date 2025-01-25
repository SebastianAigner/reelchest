package io.sebi.process

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalProcessTest {
    @Test
    fun `test echo command captures stdout`(): Unit = runBlocking {
        val output = mutableListOf<String>()
        runExternalProcess(
            "echo", "Hello, World!",
            onStdoutLine = { output.add(it) },
            directory = null
        )
        assertEquals(listOf("Hello, World!"), output)
    }

    @Test
    fun `test stderr output is captured`(): Unit = runBlocking {
        val stderr = mutableListOf<String>()
        // On Unix-like systems, this command will output to stderr
        runExternalProcess(
            "sh", "-c", "echo 'Error message' >&2",
            onStderrLine = { stderr.add(it) },
            directory = null
        )
        assertEquals(listOf("Error message"), stderr)
    }

    @Test
    fun `test working directory is respected`(): Unit = runBlocking {
        val tempDir = createTempDir()
        try {
            val output = mutableListOf<String>()
            // Create a test file in the temp directory
            File(tempDir, "test.txt").writeText("test content")

            // List directory contents
            runExternalProcess(
                "ls",
                onStdoutLine = { output.add(it) },
                directory = tempDir
            )

            assertEquals(listOf("test.txt"), output)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test invalid command throws exception`(): Unit = runBlocking {
        assertFailsWith<Exception> {
            runExternalProcess(
                "non_existent_command",
                directory = null
            )
        }
    }
}