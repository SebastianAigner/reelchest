package io.sebi.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class NetworkManager(val requestTokenProvider: RequestTokenProvider = GlobalRequestTokenProvider) {

    val logger = LoggerFactory.getLogger("Network Manager")

    val cachemap = ConcurrentHashMap<String, String>()

    private val client = HttpClient(Apache) {
        install(UserAgent) {
            agent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
        }


        install(HttpTimeout) {
            requestTimeoutMillis = null
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 30000
        }

        engine {
            //threadsCount = 8
        }
    }

    suspend fun getPage(url: String, contentType: String = "text/html"): String {
        return cachemap.getOrPut(url) {
            logger.debug("Requesting fresh page $url")
            getFreshPage(url, contentType)
        }
    }

    suspend fun getFreshPage(url: String, contentType: String = "text/html"): String {
        requestTokenProvider.takeToken()
        val res = client.head(url).body<HttpResponse>()
        val actualCT = res.headers["Content-Type"]
        if (actualCT?.contains(contentType) == false) {
            logger.warn("An origin page was requested, but the response type was not $contentType (it was $actualCT). Probably direct download, skipping.")
            // we're probably downloading an artifact directly.
            return ""
        }
        val realRes = client.get(url).body<String>()
        cachemap[url] = realRes
        return realRes
    }

    fun getRawClient(noReallyItsOkay: Boolean = false): HttpClient {
        if (!noReallyItsOkay) {
            logger.warn("Using raw client for operation. Not recommended!")
        }
        return client
    }
}