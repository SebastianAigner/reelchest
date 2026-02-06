package io.sebi.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

suspend fun runExternalProcess(
    vararg command: String,
    onStdoutLine: suspend (String) -> Unit = {},
    onStderrLine: suspend (String) -> Unit = {},
    directory: File?,
) {
    withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(*command)
        if (directory != null) {
            processBuilder.directory(directory)
        }
        val process = processBuilder.start()
        val stdoutFlow = process.inputStream.bufferedReader().lineSequence().asFlow()
        val stderrFlow = process.errorStream.bufferedReader().lineSequence().asFlow()
        val stdoutJob = launch { stdoutFlow.collect { onStdoutLine(it) } }
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
    }
}
