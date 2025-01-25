package io.sebi.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        launch {
            stdoutFlow.collect { onStdoutLine(it) }
        }
        launch { stderrFlow.collect { onStderrLine(it) } }
        process.waitFor()
    }
}
