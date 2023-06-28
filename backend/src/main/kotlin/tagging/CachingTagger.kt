package io.sebi.tagging

import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class CachingTagger : Tagger {
    val logger = LoggerFactory.getLogger("Caching Tagger")
    val delegate = AutoTagger
    val cache = mutableMapOf<NameAndTagsContainer, Set<String>>()

    data class NameAndTagsContainer(val name: String, val tags: Set<String>)

    @OptIn(ExperimentalTime::class)
    override fun tag(name: String, tags: Set<String>): Set<String> {
        val result = measureTimedValue {
            cache.getOrPut(NameAndTagsContainer(name, tags)) {
                delegate.tag(name, tags)
            }
        }
        if (result.duration.inWholeMilliseconds > 50) {
            logger.info("Took ${result.duration} to calculate tags for '$name'.")
        }

        return result.value
    }
}