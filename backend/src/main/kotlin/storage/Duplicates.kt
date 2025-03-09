package io.sebi.storage

data class Duplicates(
    val src_id: String,
    val dup_id: String,
    val distance: Long,
)