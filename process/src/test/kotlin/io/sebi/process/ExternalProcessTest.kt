package io.sebi.process

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExternalProcessTest {
    @Test
    fun `test echo command captures stdout`(): Unit = runBlocking {
        val output = mutableListOf<String>()
        runExternalProcess(
            "echo", "Hello, World!",
            stdoutHandler = StdoutHandler.Lines { output.add(it) },
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
        val tempDir = createTempDirectory().toFile()
        try {
            val output = mutableListOf<String>()
            // Create a test file in the temp directory
            File(tempDir, "test.txt").writeText("test content")

            // List directory contents
            runExternalProcess(
                "ls",
                stdoutHandler = StdoutHandler.Lines { output.add(it) },
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

    @Test
    fun `test binary stdout handler receives bytes`(): Unit = runBlocking {
        val output = ByteArrayOutputStream()
        runExternalProcess(
            "sh", "-c", "printf '\\x01\\x02\\x03'",
            stdoutHandler = StdoutHandler.Bytes { input ->
                input.copyTo(output)
            },
            directory = null
        )
        assertEquals(listOf<Byte>(1, 2, 3), output.toByteArray().toList())
    }

    @Test
    fun `test cancellation stops external process`(): Unit = runBlocking {
        val tempDir = createTempDirectory()
        val pidFile = tempDir.resolve("pid.txt")
        val job = launch {
            runExternalProcess(
                "sh", "-c", "echo $$ > ${pidFile.toAbsolutePath()}; sleep 10",
                directory = tempDir.toFile()
            )
        }
        val pidReady = withTimeoutOrNull(2000) {
            while (!Files.exists(pidFile)) {
                delay(25)
            }
            true
        }
        assertTrue(pidReady == true)
        val pid = Files.readString(pidFile).trim().toLong()
        delay(100)
        job.cancel()
        val joined = withTimeoutOrNull(2000) {
            job.join()
            true
        }
        assertTrue(joined == true)
        assertTrue(job.isCancelled)
        val stopped = withTimeoutOrNull(2000) {
            while (ProcessHandle.of(pid).map { it.isAlive }.orElse(false)) {
                delay(25)
            }
            true
        }
        assertTrue(stopped == true)
    }
}
