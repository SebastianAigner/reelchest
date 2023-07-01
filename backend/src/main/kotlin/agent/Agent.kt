package io.sebi.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.sebi.library.MediaLibraryEntry
import io.sebi.phash.DHash
import io.sebi.phash.getMinimalDistance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

val client = HttpClient() { install(ContentNegotiation) { json() } }

// Remote agent that can calculate duplicates remotely
@OptIn(ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {
    // get IP from args
    runBlocking {
        val ids = client.get("http://${args[0]}:8080/api/mediaLibrary")
            .body<List<MediaLibraryEntry>>().map { it.id }

        val all: List<IdToHashes> = ids
            .map { async { IdToHashes(it, getHashesForId(it)) } }
            .awaitAll()

        val allResults = ids.map { elem ->
            val hashes = getHashesForId(elem)
            val dup = calculateDuplicate(IdToHashes(elem, hashes), all)
            println("$elem -> ${dup.id} (${dup.distance})")
            DuplicateResult(elem, dup.id, dup.distance)
        }
        println(allResults.sortedBy { it.distance }.joinToString("\n"))
    }
}

data class DuplicateResult(val fromId: String, val toId: String, val distance: Int)

@OptIn(ExperimentalUnsignedTypes::class)
val hashForId = ConcurrentHashMap<String, ULongArray>()

val sem = Semaphore(32)

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