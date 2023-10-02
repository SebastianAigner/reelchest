package io.sebi

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FeedEntry(
    val imageUrl: String,
    val originUrl: String,
    val uuid: String,
    val priority: Int = 0
)


fun FeedEntry(e: Entity): FeedEntry {
    return FeedEntry(
        imageUrl = e.getProperty("imageUrl") as String,
        originUrl = e.getProperty("originUrl") as String,
        uuid = e.getProperty("uuid") as String,
        priority = e.getProperty("priority") as? Int ?: 0
    )
}

fun Entity.setFeedEntryFields(feedEntry: FeedEntry) {
    setProperty("imageUrl", feedEntry.imageUrl)
    setProperty("originUrl", feedEntry.originUrl)
    setProperty("uuid", feedEntry.uuid)
    setProperty("priority", feedEntry.priority)
}

class Feed { // TODO: I'm beginning to think it might be nicer to just extract a proper "PersistedQueue<T>" here.
    val entityStore: PersistentEntityStore = PersistentEntityStores.newInstance(".") // TODO: This is a closable, let's make sure we close it. :)

    fun getFeed(): List<FeedEntry> = with(entityStore) {
        val queueEntries = this.computeInTransaction {
            it.sort("queueEntry", "priority", true).map { FeedEntry(it) }
        }
        return queueEntries
    }

    fun accept(uuid: String): FeedEntry? {
        // just remove it, someone else will take care of the real downloading
        with(entityStore) {
            return computeInTransaction {
                val matchingEntry = it.find("queueEntry", "uuid", uuid).first
                val feedEntry = matchingEntry?.let { FeedEntry(it) }
                matchingEntry?.delete()
                feedEntry
            }
        }
    }

    fun decline(uuid: String) {
        with(entityStore) {
            executeInTransaction {
                val matchingEntry = it.find("queueEntry", "uuid", uuid)
                matchingEntry.first?.delete()
            }
        }
    }

    fun skip(uuid: String) {
        with(entityStore) {
            executeInTransaction {
                val matchingEntry = it.find("queueEntry", "uuid", uuid)
                val first = matchingEntry.first
                first?.setProperty("priority", (first.getProperty("priority") as? Int ?: 0) + 1)
            }
        }
    }

    private fun addEntry(feedEntry: FeedEntry) {
        with(entityStore) {
            executeInTransaction {
                val entry = it.newEntity("queueEntry")
                entry.setFeedEntryFields(feedEntry)
            }
        }
    }

    fun CoroutineScope.updateFeed() {
        launch {
            supervisorScope {
                // TODO: This is just a stub for now until we put the machinery in place to either request or receive feed items from other participants in the system
                addEntry(
                    FeedEntry(
                        imageUrl = "http://192.168.178.165:8080/api/mediaLibrary/19ef95a20c401bfec0537fc359f3a7f4bc38248ccb2e3483c57df84e3b2a407e/randomThumb",
                        originUrl = "",
                        uuid = UUID.randomUUID().toString()
                    )
                )
                addEntry(
                    FeedEntry(
                        imageUrl = "http://192.168.178.165:8080/api/mediaLibrary/26e09f48b9daddb84b0af85976a3ab0e80e13ec587df581bb474ab0ab8de626/randomThumb",
                        originUrl = "",
                        uuid = UUID.randomUUID().toString()
                    )
                )
                addEntry(
                    FeedEntry(
                        imageUrl = "http://192.168.178.165:8080/api/mediaLibrary/f396ff432f9ef60e41ff25a3719d6ee6db2282eb286c5f9e8b680320b26b7ea2/randomThumb",
                        originUrl = "",
                        uuid = UUID.randomUUID().toString()
                    )
                )
            }
        }
    }
}