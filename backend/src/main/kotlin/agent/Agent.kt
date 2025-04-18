package io.sebi.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.sebi.api.dtos.DuplicatesDTO
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.id
import io.sebi.phash.DHash
import io.sebi.phash.getMinimalDistance
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

val client = HttpClient() {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
}

// Remote agent that can calculate duplicates remotely
@OptIn(ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {
    // get IP from args
    runBlocking {
        val ids = client.get("http://${args[0]}:8080/api/mediaLibrary")
            .body<List<MediaLibraryEntry>>().map { it.id }

        val allHashes: List<IdToHashes> = ids
            .map { async { IdToHashes(it, getHashesForId(it)) } }
            .awaitAll()

        val allResults = channelFlow<DuplicateResult> {
            ids.map { elem ->
                launch(Dispatchers.Default) {
                    val hashes = getHashesForId(elem)
                    val dup = calculateDuplicate(IdToHashes(elem, hashes), allHashes)

                    println("$elem -> ${dup.id} (${dup.distance})")
                    send(DuplicateResult(elem, dup.id, dup.distance))
                }
            }
        }
            .filter {
                it.toId != "SOURCE_HAD_NO_HASHES"
            }
            .onEach {
                client.post("http://${args[0]}:8080/api/mediaLibrary/duplicates/${it.fromId}") {
                    contentType(ContentType.Application.Json)
                    setBody(DuplicatesDTO(it.fromId, it.toId, it.distance.toLong()))
                }
            }
            .toList()
        println(allResults.sortedBy { it.distance }.joinToString("\n"))
    }
}

data class DuplicateResult(val fromId: String, val toId: String, val distance: Int)

@OptIn(ExperimentalUnsignedTypes::class)
val hashForId = ConcurrentHashMap<String, ULongArray>()

val sem = Semaphore(4)

@OptIn(ExperimentalUnsignedTypes::class)
suspend fun getHashesForId(id: String): ULongArray {
    return hashForId.getOrPut(id) {
        sem.withPermit {
            println("Getting hashes for $id")
            val res = client
                .get("http://192.168.178.165:8080/api/mediaLibrary/$id/hash.json")
            if (res.status == HttpStatusCode.NotFound) return@getOrPut ULongArray(0)
            return@getOrPut res.body<ULongArray>()
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
data class IdToHashes(val id: String, val hashes: ULongArray)

@OptIn(ExperimentalUnsignedTypes::class)
fun calculateDuplicate(current: IdToHashes, all: List<IdToHashes>): IdWithDistance {
    if (current.hashes.isEmpty()) return IdWithDistance("SOURCE_HAD_NO_HASHES", Int.MAX_VALUE)
    // we randomly pick a handful of hashes from our candidate.
    val handful = ULongArray(100) { current.hashes.random() }
    // we find the global minimum: which of the other library entries has the lowest cumulative distance?

    val mostLikelyDuplicate = all.minByOrNull { other ->
        if (other.id == current.id || other.hashes.isEmpty()) return@minByOrNull Int.MAX_VALUE
        handful.sumOf {
            getMinimalDistance(other.hashes, DHash(it))
        }
    }!!

    val cumulativeDistance = handful.sumOf {
        getMinimalDistance(mostLikelyDuplicate.hashes, DHash(it))
    }
    return IdWithDistance(mostLikelyDuplicate.id, cumulativeDistance)
}

data class IdWithDistance(val id: String, val distance: Int)