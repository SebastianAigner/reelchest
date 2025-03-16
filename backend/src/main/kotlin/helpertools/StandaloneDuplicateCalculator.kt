package io.sebi.helpertools

import io.sebi.duplicatecalculator.calculateLikelyDuplicateForDHashArray
import io.sebi.phash.readULongs
import io.sebi.storage.Duplicates
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.time.Duration
import kotlin.time.measureTimedValue

private const val INPUT_DIR = "all-remote-hashes"

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val inputDir = File(INPUT_DIR)
    val (index, time) = measureTimedValue { buildIndexForDirectory(inputDir) }
    println("Built index in $time.")
    val allDuplicates = MutableStateFlow(emptyList<DuplicatesWithTiming>())
    runBlocking {
        val inspector = launch {
            while (true) {
                delay(1000)
                println(
                    "Checked ${allDuplicates.value.size} duplicates so far (average time: ${
                        allDuplicates.value
                            .map { it.time }
                            .average()
                    }s)."
                )
            }
        }
        findDuplicatesForEntireIndex(index).collect {
            allDuplicates.update { oldList -> oldList + it }
        }
        inspector.cancelAndJoin()
    }
    println(allDuplicates.value.sortedBy { it.duplicates.distance }.take(100).joinToString("\n"))
}

fun List<Duration>.average(): Duration {
    if (this.isEmpty()) return Duration.ZERO
    return this.fold(Duration.ZERO) { acc, d -> acc + d } / this.size
}

@OptIn(ExperimentalUnsignedTypes::class)
fun findDuplicatesForEntireIndex(index: Map<String, ULongArray>): Flow<DuplicatesWithTiming> {
    return channelFlow {
        for (entry in index.entries) {
            val (needle, needleHashes) = entry
            val haystack = index.filter { it.key != needle }.map { it.toPair() }
            launch(Dispatchers.Default) {
                val (likelyDup, dupTime) = measureTimedValue {
                    calculateLikelyDuplicateForDHashArray(
                        needleHashes,
                        haystack
                    )
                }
                println("Calculated $needle <==(dist=${likelyDup.distance})==> ${likelyDup.id} in $dupTime.")
                send(
                    DuplicatesWithTiming(
                        Duplicates(needle, likelyDup.id, likelyDup.distance.toLong()),
                        dupTime
                    )
                )
            }
        }
    }
}

data class DuplicatesWithTiming(val duplicates: Duplicates, val time: Duration)

@OptIn(ExperimentalUnsignedTypes::class)
fun buildIndexForDirectory(dir: File): Map<String, ULongArray> {
    return buildMap {
        for (file in dir.listFiles()!!) {
            val ulongs = file.readULongs()
            if (ulongs.size < 10) continue
            put(
                file.nameWithoutExtension,
                ulongs
            ) // we try an optimization here: we put all the zero-bitted hashes in the front.
        }
    }
}
