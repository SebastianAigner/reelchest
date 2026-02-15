package io.sebi.storage

import kotlinx.serialization.Serializable

@Serializable
enum class MediaProbeStatus {
    SUCCESS,
    FAILED,
}

@Serializable
data class MediaInformation(
    val libraryEntryId: String,
    val mimeType: String?,
    val codecType: String?,
    val codecName: String?,
    val width: Int?,
    val height: Int?,
    val bitRate: Long?,
    val frameRate: Double?,
    val fileSize: Long?,
    val durationSeconds: Double?,
    val probeStatus: MediaProbeStatus,
    val probeError: String?,
    val lastProbeAt: Long,
)
