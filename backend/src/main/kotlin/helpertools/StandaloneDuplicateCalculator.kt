package io.sebi.helpertools

import io.sebi.duplicatecalculator.calculateLikelyDuplicateForDHashArray
import io.sebi.phash.readULongs
import io.sebi.storage.Duplicates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.measureTimedValue

private const val INPUT_DIR = "all-remote-hashes"

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val inputDir = File(INPUT_DIR)
    val (index, time) = measureTimedValue { buildIndexForDirectory(inputDir) }
    println("Built index in $time.")
    val duplicates = runBlocking {
        index.entries.take(100).map {
            val (needle, needleHashes) = it
            val seq = index.asSequence().filter { it.key != needle }.map { it.toPair() }
            async(Dispatchers.Default) {
                val (likelyDup, dupTime) = measureTimedValue {
                    calculateLikelyDuplicateForDHashArray(
                        needleHashes,
                        seq
                    )
                }
                println("Calculated most likely duplicate (dist=${likelyDup.distance} for $needle in $dupTime.")
                Duplicates(needle, likelyDup.id, likelyDup.distance.toLong())
            }
        }.awaitAll()
    }
    println(duplicates.sortedBy { it.distance }.joinToString("\n"))
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
