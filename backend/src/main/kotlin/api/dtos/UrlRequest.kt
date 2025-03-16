package io.sebi.api.dtos

import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable

@TypeScript
@Serializable
data class UrlRequest(val url: String)