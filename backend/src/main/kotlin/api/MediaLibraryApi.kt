package io.sebi.api

import dz.jtsgen.annotations.TypeScript
import io.sebi.storage.Duplicates
import kotlinx.serialization.Serializable

@TypeScript
@Serializable
data class DuplicatesDTO(
    val src_id: String,
    val dup_id: String,
    val distance: Long,
)

fun DuplicatesDTO.Companion.from(d: Duplicates): DuplicatesDTO {
    return DuplicatesDTO(d.src_id, d.dup_id, d.distance)
}

