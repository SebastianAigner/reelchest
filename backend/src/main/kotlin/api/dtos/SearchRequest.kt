package io.sebi.api.dtos

import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable

@TypeScript
@Serializable
data class SearchRequest(val term: String, val offset: Int = 0)