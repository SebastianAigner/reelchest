package io.sebi.urlmatcher

import io.sebi.network.NetworkManager
import io.sebi.urldecoder.urlmatcher.MetadataProvider

abstract class UrlMatcher(protected val networkManager: NetworkManager) {
    abstract suspend fun matchUrl(url: String): List<String>?
    abstract fun getMetadataProvider(): MetadataProvider
    abstract fun getMetadataProviderForUrl(url: String): MetadataProvider?
}