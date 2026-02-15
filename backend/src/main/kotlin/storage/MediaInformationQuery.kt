package io.sebi.storage

import kotlinx.serialization.Serializable

@Serializable
enum class MediaInfoSort {
    RESOLUTION,
    FRAME_RATE,
    FILE_SIZE,
    DURATION,
    WIDTH,
    HEIGHT,
    BIT_RATE,
}

@Serializable
enum class SortOrder {
    ASC,
    DESC,
}

@Serializable
data class MediaInformationSummary(
    val id: String,
    val title: String,
    val originUrl: String,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Double?,
    val fileSize: Long?,
    val bitRate: Long?,
    val durationSeconds: Double?,
)
