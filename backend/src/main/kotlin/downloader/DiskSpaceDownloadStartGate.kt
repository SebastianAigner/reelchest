package io.sebi.downloader

import io.sebi.disk.DiskSpaceProvider
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DiskSpaceDownloadStartGate(
    private val diskSpaceProvider: DiskSpaceProvider,
    private val minFreeBytes: Long = MIN_FREE_BYTES,
    private val pollInterval: Duration = 30.seconds,
    private val logger: Logger = LoggerFactory.getLogger("Download Start Gate"),
) : DownloadStartGate {
    override suspend fun awaitPermit() {
        var logged = false
        while (true) {
            val usableBytes = diskSpaceProvider.usableBytes()
            if (hasEnoughSpace(usableBytes, minFreeBytes)) {
                return
            }
            if (!logged) {
                logger.warn(
                    "Insufficient disk space for new downloads. Usable: $usableBytes bytes, " +
                            "required: > $minFreeBytes bytes."
                )
                logged = true
            }
            delay(pollInterval)
        }
    }

    companion object {
        const val MIN_FREE_BYTES: Long = 25L * 1024 * 1024 * 1024
    }
}

internal fun hasEnoughSpace(usableBytes: Long, minFreeBytes: Long): Boolean {
    return usableBytes > minFreeBytes
}
