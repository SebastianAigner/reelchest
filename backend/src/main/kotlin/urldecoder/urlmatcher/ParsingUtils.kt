package io.sebi.metadataproviders

import io.sebi.network.NetworkManager

suspend fun NetworkManager.loadAndMatch(url: String, regex: Regex): MatchResult.Destructured {
    val str = getPage(url)
    val res = regex.find(str)!!.destructured
    return res
}