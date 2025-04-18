package io.sebi.helpertools

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.sebi.api.dtos.DuplicatesDTO
import io.sebi.api.dtos.from
import io.sebi.duplicatecalculator.calculateLikelyDuplicateForDHashArray
import io.sebi.phash.readULongs
import io.sebi.storage.Duplicates
import io.sebi.utils.average
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
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
                    })."
                )
            }
        }
        val semaphore = Semaphore(4)
        findDuplicatesForEntireIndex(index).collect {
            allDuplicates.update { oldList -> oldList + it }
            launch {
                semaphore.withPermit {
                    val resp = communicateDuplicateToRemote("", it.duplicate)
                    println(resp)
                }
            }
        }
        inspector.cancelAndJoin()
    }
    println(allDuplicates.value.sortedBy { it.duplicate.distance }.take(100).joinToString("\n"))
}

/**
 * Communicates a duplicate to a remote server.
 *
 * @param remoteAddress The address of the remote server, e.g. "http://example.com:8080"
 * @param duplicate The duplicate to send
 */
val client = HttpClient(CIO)
suspend fun communicateDuplicateToRemote(remoteAddress: String, duplicate: Duplicates): HttpStatusCode {
    try {
        val duplicateDto = DuplicatesDTO.from(duplicate)
        val response = client.post("$remoteAddress/api/mediaLibrary/${duplicate.src_id}/storedDuplicate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(DuplicatesDTO.serializer(), duplicateDto))
        }
        return response.status
    } catch (e: Exception) {
        e.printStackTrace()
        return HttpStatusCode.fromValue(418)
    } finally {
    }
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

data class DuplicatesWithTiming(val duplicate: Duplicates, val time: Duration)

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
