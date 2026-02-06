package io.sebi.downloader

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.*

class DomainDownloadSchedulerTest {
    private fun task(url: String) = DownloadTask(
        originUrl = url,
        getDirectDownloadUrls = { emptyList() },
        onPostProcess = { error("unused") },
        onComplete = {}
    )

    @Test
    fun differentDomainsCanRunInParallel() {
        runBlocking {
            val scheduler = DomainDownloadScheduler { url -> url.substringBefore("/") }
            scheduler.enqueue(task("alpha.com/one"))
            scheduler.enqueue(task("bravo.com/one"))

            val first = async { scheduler.takeNext() }
            val second = async { scheduler.takeNext() }

            val results = setOf(first.await().originUrl, second.await().originUrl)
            assertTrue(results.containsAll(listOf("alpha.com/one", "bravo.com/one")))
        }
    }

    @Test
    fun sameDomainIsSerialized() {
        runBlocking {
            val scheduler = DomainDownloadScheduler { "example.com" }
            scheduler.enqueue(task("example.com/a"))
            scheduler.enqueue(task("example.com/b"))

            val first = async { scheduler.takeNext() }
            val second = async { scheduler.takeNext() }

            val firstTask = first.await()
            val secondImmediate = withTimeoutOrNull(200) { second.await() }
            assertNull(secondImmediate)

            scheduler.release(firstTask.originUrl)
            val secondTask = withTimeoutOrNull(200) { second.await() }
            assertNotNull(secondTask)
        }
    }

    @Test
    fun skipsBlockedDomainToAllowOtherDomains() {
        runBlocking {
            val scheduler = DomainDownloadScheduler { url -> url.substringBefore(":") }
            scheduler.enqueue(task("alpha:1"))
            scheduler.enqueue(task("alpha:2"))
            scheduler.enqueue(task("bravo:1"))

            val first = scheduler.takeNext()
            val second = scheduler.takeNext()

            assertEquals("alpha:1", first.originUrl)
            assertEquals("bravo:1", second.originUrl)
        }
    }

    @Test
    fun extractsEtldPlusOneFromOriginUrl() {
        assertEquals(
            "example.co.uk",
            originUrlDomainKey("https://media.sub.example.co.uk/path")
        )
    }

    @Test
    fun preservesLocalHostnames() {
        assertEquals(
            "localhost",
            originUrlDomainKey("localhost:8080/video")
        )
    }

    @Test
    fun fileUrlsReturnUnknownDomain() {
        assertEquals(
            "unknown",
            originUrlDomainKey("file:///tmp/test.mp4")
        )
    }
}
