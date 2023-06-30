package io.sebi.duplicatecalculator

import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.phash.DHash
import io.sebi.phash.getMinimalDistance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

data class EntryWithDistance(val entry: MediaLibraryEntry, val distance: Int)

private val logger = LoggerFactory.getLogger("Duplicate Calculator")

class DuplicateCalculator(val mediaLibrary: MediaLibrary) {
    var duplicatesMap: Map<MediaLibraryEntry, EntryWithDistance>? = null

    @OptIn(ExperimentalTime::class)
    suspend fun calculateDuplicates() {
        val x = kotlin.time.TimeSource.Monotonic.markNow()
        logger.info("Starting duplicates calculation...")

        val dispatcher = Dispatchers.Default

        val calculated =
            coroutineScope {
                mediaLibrary.entries.toList().map {
                    async(dispatcher) {
                        val duplicateForEntry = calculateDuplicateForEntry(it)
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
        mediaLibrary.entries.mapNotNull { curr ->
            curr.getDHashes()
                ?.let { dhash ->
                    return@mapNotNull curr to dhash
                }
            null
        }.asSequence()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getRestLibrary(entry: MediaLibraryEntry): Sequence<Pair<MediaLibraryEntry, ULongArray>> {
        return mediaLibWithHashes
            .filterNot {
                it.first.id == entry.id
            }
    }


    @OptIn(ExperimentalUnsignedTypes::class)
    fun calculateDuplicateForEntry(entry: MediaLibraryEntry): EntryWithDistance? {
        val restLibrary = getRestLibrary(entry)
        // we randomly pick a handful of hashes from our candidate.
        val entryHashes = entry.getDHashes() ?: return null
        val handful = ULongArray(100) { entryHashes.random() }
        // we find the global minimum: which of the other library entries has the lowest cumulative distance?

        val mostLikelyDuplicate = restLibrary.minByOrNull { (entry, dhashes) ->
            handful.sumOf {
                getMinimalDistance(dhashes, DHash(it))
            }
        }!!

        val cumulativeDistance = handful.sumOf {
            getMinimalDistance(mostLikelyDuplicate.second, DHash(it))
        }
        return EntryWithDistance(mostLikelyDuplicate.first, cumulativeDistance)
    }
}