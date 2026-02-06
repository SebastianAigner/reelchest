package io.sebi.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed interface StdoutHandler {
    class Lines(val onLine: suspend (String) -> Unit = {}) : StdoutHandler
    class Bytes(val onBytes: suspend (InputStream) -> Unit) : StdoutHandler
}

suspend fun runExternalProcess(
    vararg command: String,
    stdoutHandler: StdoutHandler = StdoutHandler.Lines(),
    onStderrLine: suspend (String) -> Unit = {},
    directory: File?,
): Int {
    return withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(*command)
        if (directory != null) {
            processBuilder.directory(directory)
        }
        val process = processBuilder.start()
        val stderrFlow = process.errorStream.bufferedReader().lineSequence().asFlow()
        val stdoutJob = when (stdoutHandler) {
            is StdoutHandler.Lines -> {
                val stdoutFlow = process.inputStream.bufferedReader().lineSequence().asFlow()
                launch { stdoutFlow.collect { stdoutHandler.onLine(it) } }
            }

            is StdoutHandler.Bytes -> {
                launch { stdoutHandler.onBytes(process.inputStream) }
            }
        }
        val stderrJob = launch { stderrFlow.collect { onStderrLine(it) } }
        try {
            runInterruptible {
                process.waitFor()
            }
            stdoutJob.join()
            stderrJob.join()
        } finally {
            stdoutJob.cancel()
            stderrJob.cancel()
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
            }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
        return@withContext process.exitValue()
    }
}
