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
import kotlin.time.measureTimedValue

private const val INPUT_DIR = "all-remote-hashes"

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val inputDir = File(INPUT_DIR)
    val (index, time) = measureTimedValue { buildIndexForDirectory(inputDir) }
    println("Built index in $time.")
    val allDuplicates = MutableStateFlow(emptyList<Duplicates>())
    runBlocking {
        launch {
            while (true) {
                delay(1000)
                println("Checked ${allDuplicates.value.size} duplicates so far.")
            }
        }
        findDuplicatesForEntireIndex(index).collect {
            allDuplicates.update { oldList -> oldList + it }
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun findDuplicatesForEntireIndex(index: Map<String, ULongArray>): Flow<Duplicates> {
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
                send(Duplicates(needle, likelyDup.id, likelyDup.distance.toLong()))
            }
        }
    }
}

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
