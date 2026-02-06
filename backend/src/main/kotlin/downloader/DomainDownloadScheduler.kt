package io.sebi.downloader

import com.google.common.net.InternetDomainName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.IDN
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

private const val UNKNOWN_DOMAIN = "unknown"

fun originUrlDomainKey(originUrl: String): String {
    val host = extractHost(originUrl) ?: return UNKNOWN_DOMAIN
    val normalizedHost = try {
        IDN.toASCII(host)
    } catch (_: IllegalArgumentException) {
        host
    }
    val lowerHost = normalizedHost.lowercase()
    return try {
        val domain = InternetDomainName.from(lowerHost)
        if (domain.isUnderPublicSuffix) {
            domain.topPrivateDomain().toString()
        } else {
            lowerHost
        }
    } catch (_: IllegalArgumentException) {
        lowerHost
    }
}

private fun extractHost(originUrl: String): String? {
    fun hostFrom(uri: URI): String? = uri.host?.takeIf { it.isNotBlank() }
    return try {
        val uri = URI(originUrl)
        hostFrom(uri) ?: if (!originUrl.contains("://") && !originUrl.startsWith("file:", ignoreCase = true)) {
            hostFrom(URI("http://$originUrl"))
        } else {
            null
        }
    } catch (_: Exception) {
        if (!originUrl.contains("://") && !originUrl.startsWith("file:", ignoreCase = true)) {
            try {
                hostFrom(URI("http://$originUrl"))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
}

class DomainDownloadScheduler(
    private val domainKeyer: (String) -> String = ::originUrlDomainKey,
) {
    private val mutex = Mutex()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val pending = ArrayDeque<DownloadTask>()
    private val activeDomains = mutableSetOf<String>()
    private val pendingSnapshot = AtomicReference<List<DownloadTask>>(emptyList())

    fun pendingSnapshot(): List<DownloadTask> = pendingSnapshot.get()

    suspend fun enqueue(task: DownloadTask) {
        mutex.withLock {
            pending.addLast(task)
            refreshSnapshotLocked()
            wakeups.trySend(Unit)
        }
    }

    suspend fun takeNext(): DownloadTask {
        while (true) {
            mutex.withLock {
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    val domain = domainKeyer(task.originUrl)
                    if (activeDomains.add(domain)) {
                        iterator.remove()
                        refreshSnapshotLocked()
                        return task
                    }
                }
            }
            wakeups.receive()
        }
    }

    suspend fun release(originUrl: String) {
        val domain = domainKeyer(originUrl)
        mutex.withLock {
            if (activeDomains.remove(domain)) {
                wakeups.trySend(Unit)
            }
        }
    }

    private fun refreshSnapshotLocked() {
        pendingSnapshot.set(pending.toList())
    }
}
