package io.sebi.api

import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable

@TypeScript
@Serializable
data class DuplicateResponse(val entryId: String, val possibleDuplicateId: String, val distance: Int)