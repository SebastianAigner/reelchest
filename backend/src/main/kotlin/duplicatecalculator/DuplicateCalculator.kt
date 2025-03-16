package io.sebi.duplicatecalculator

import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.getDHashesFromDisk
import io.sebi.library.id
import io.sebi.phash.DHash
import io.sebi.phash.getMinimalDistance
import kotlinx.coroutines.*
import org.checkerframework.dataflow.qual.SideEffectFree
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

data class IdWithDistance(val id: String, val distance: Int)

private val logger = LoggerFactory.getLogger("Duplicate Calculator")

class DuplicateCalculator(val mediaLibrary: MediaLibrary) {
    var duplicatesMap: Map<MediaLibraryEntry, IdWithDistance>? = null

    @OptIn(ExperimentalTime::class, ExperimentalUnsignedTypes::class)
    suspend fun calculateDuplicates() {
        val x = kotlin.time.TimeSource.Monotonic.markNow()
        logger.info("Starting duplicates calculation...")

        val dispatcher = Dispatchers.Default

        val calculated =
            coroutineScope {
                mediaLibrary.getEntries().toList().map {
                    async(dispatcher) {
                        val duplicateForEntry =
                            calculateLikelyDuplicateForDHashArray(
                                it.getDHashesFromDisk() ?: return@async null,
                                getRestLibrary(it.id)
                            )
                        it to (duplicateForEntry ?: return@async null)
                    }
                }
                    .awaitAll()
                    .filterNotNull()
                    .toMap()
            }

        duplicatesMap = calculated
        logger.info("Finished duplicates calculation in ${x.elapsedNow()}")
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    val mediaLibWithHashes: Sequence<Pair<MediaLibraryEntry, ULongArray>> by lazy {
        runBlocking {
            mediaLibrary.getEntries().mapNotNull { curr ->
                curr.getDHashesFromDisk()
                    ?.let { dhash ->
                        return@mapNotNull curr to dhash
                    }
                null
            }.asSequence()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getRestLibrary(entryId: String): Sequence<Pair<String, ULongArray>> {
        return mediaLibWithHashes
            .filterNot {
                it.first.id == entryId
            }.map {
                it.first.id to it.second
            }
    }


}

@SideEffectFree
@OptIn(ExperimentalUnsignedTypes::class)
fun calculateLikelyDuplicateForDHashArray(
    needleDHashes: ULongArray,
    haystack: Sequence<Pair<String, ULongArray>>,
): IdWithDistance {
    // we randomly pick a handful of hashes from our candidate.
    val someNeedleHashes = ULongArray(100) { needleDHashes.random() }
    // we find the global minimum: which of the other library entries has the lowest cumulative distance?

    val mostLikelyDuplicate = haystack.minByOrNull { (_, haystackElemHashes) ->
        someNeedleHashes.sumOf {
            getMinimalDistance(haystackElemHashes, DHash(it))
        }
    }!!

    val cumulativeDistance = someNeedleHashes.sumOf {
        getMinimalDistance(mostLikelyDuplicate.second, DHash(it))
    }
    return IdWithDistance(mostLikelyDuplicate.first, cumulativeDistance)
}
