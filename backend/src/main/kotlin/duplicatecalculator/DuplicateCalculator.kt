package io.sebi.duplicatecalculator

import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.phash.DHash
import io.sebi.phash.getMinimalDistance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

        val calculated = mediaLibrary.entries.toList().parallelStream().map {
            it to (runBlocking { calculateDuplicateForEntry(it) } // todo: highly questionable hacky code
                ?: return@map null)
        }.toList().filterNotNull().toMap()

        duplicatesMap = calculated
        logger.info("Finished duplicates calculation in ${x.elapsedNow()}")
    }


    @OptIn(ExperimentalStdlibApi::class)
    suspend fun calculateDuplicateForEntry(entry: MediaLibraryEntry): EntryWithDistance? {
        val restLibrary = mediaLibrary.entries
            .filterNot { it.id == entry.id }
            .mapNotNull { curr ->
                yield() // todo: temporary fix; let's see if this helps with videos not loading properly while duplicate calculation is running
                curr.getDHashes()
                    ?.let { dhash ->
                        return@mapNotNull curr to dhash
                    }
                null
            }
        // we randomly pick a handful of hashes from our candidate.
        val entryHashes = entry.getDHashes() ?: return null
        val handful = buildList<DHash> {
            repeat(100) {
                add(entryHashes.random())
            }
        }
        // we find the global minimum: which of the other library entries has the lowest cumulative distance?

        val mostLikelyDuplicate = restLibrary.minByOrNull { (entry, dhashes) ->
            handful.sumOf {
                dhashes.getMinimalDistance(it)
            }
        }!!

        val cumulativeDistance = handful.sumOf {
            mostLikelyDuplicate.second.getMinimalDistance(it) // todo: we can probably save some calulations here, but this should be relatively cheap.
        }
        return EntryWithDistance(mostLikelyDuplicate.first, cumulativeDistance)
    }
}