package io.sebi.helpertools

import io.sebi.duplicatecalculator.calculateLikelyDuplicateForDHashArray
import io.sebi.phash.readULongs
import java.io.File
import kotlin.time.measureTimedValue

private const val INPUT_DIR = "all-remote-hashes"

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val inputDir = File(INPUT_DIR)
    val (index, time) = measureTimedValue { buildIndexForDirectory(inputDir) }
    println("Built index in $time.")
    val (needle, needleHashes) = index.entries.first()
    val seq = index.asSequence().filter { it.key != needle }.map { it.toPair() }
    val (likelyDup, dupTime) = measureTimedValue { calculateLikelyDuplicateForDHashArray(needleHashes, seq) }
    println("Calculated $likelyDup for $needle in $dupTime.")
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
