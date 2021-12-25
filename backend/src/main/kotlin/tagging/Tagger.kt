package io.sebi.tagging

interface Tagger {
    fun tag(name: String, tags: Set<String>): Set<String>
}