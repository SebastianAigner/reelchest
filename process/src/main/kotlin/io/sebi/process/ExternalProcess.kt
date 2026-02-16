package io.sebi.process

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed interface StdoutHandler {
    class Lines(val onLine: suspend (String) -> Unit = {}) : StdoutHandler
    class Bytes(val onBytes: suspend (InputStream) -> Unit) : StdoutHandler
}

suspend fun runExternalProcess(
    vararg command: String,
    stdoutHandler: StdoutHandler = StdoutHandler.Lines(),
    onStderrLine: suspend (String) -> Unit = {},
    directory: File?,
    processRef: AtomicReference<Process?>? = null,
): Int {
    return withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(*command)
        if (directory != null) {
            processBuilder.directory(directory)
        }
        val process = processBuilder.start()
        processRef?.set(process)
        val completionHandler = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException && process.isAlive) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }
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
        } catch (e: CancellationException) {
            if (process.isAlive) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
            throw e
        } finally {
            stdoutJob.cancel()
            stderrJob.cancel()
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
            }
            try {
                process.inputStream.close()
            } catch (_: Exception) {
                // ignore close failures
            }
            try {
                process.errorStream.close()
            } catch (_: Exception) {
                // ignore close failures
            }
            try {
                process.outputStream.close()
            } catch (_: Exception) {
                // ignore close failures
            }
            completionHandler.dispose()
        }
        return@withContext process.exitValue()
    }
}
