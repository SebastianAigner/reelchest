package io.sebi.urldecoder.urlmatcher

import io.sebi.network.NetworkManager

abstract class MetadataProvider(protected val networkManager: NetworkManager) {
    abstract suspend fun getTitle(url: String): String
    abstract suspend fun getTags(url: String): Set<String>
}