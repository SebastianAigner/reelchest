package io.sebi.helpertools

import io.sebi.duplicatecalculator.calculateLikelyDuplicateForDHashArray
import io.sebi.phash.readULongs
import io.sebi.storage.Duplicates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        launch {
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
    }
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
            val seq = index.asSequence().filter { it.key != needle }.map { it.toPair() }
            launch(Dispatchers.Default) {
                val (likelyDup, dupTime) = measureTimedValue {
                    calculateLikelyDuplicateForDHashArray(
                        needleHashes,
                        seq
                    )
                }
                println("Calculated most likely duplicate (dist=${likelyDup.distance} for $needle in $dupTime.")
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
            put(file.nameWithoutExtension, ulongs)
        }
    }
}
