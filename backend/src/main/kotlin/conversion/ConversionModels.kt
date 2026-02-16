package io.sebi.conversion

import kotlinx.serialization.Serializable

const val CONVERTED_TAG = "converted"
const val ORIGIN_TAG_PREFIX = "orig:"

@Serializable
enum class ConversionMode {
    IMMEDIATE,
    SCHEDULED,
}

@Serializable
data class ConversionOptions(
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val fps: Int? = null,
    val crf: Int = 21,
    val audioBitrateKbps: Int = 160,
)

@Serializable
data class ConversionRequest(
    val mode: ConversionMode,
    val options: ConversionOptions,
    val convertAndDeleteOriginal: Boolean = true,
)

@Serializable
data class ConversionTask(
    val id: String,
    val sourceId: String,
    val sourceTitle: String,
    val mode: ConversionMode,
    val options: ConversionOptions,
    val convertAndDeleteOriginal: Boolean = true,
    val createdAt: Long,
)

@Serializable
data class ProblematicConversionTask(
    val id: String,
    val sourceId: String,
    val sourceTitle: String,
    val mode: ConversionMode,
    val options: ConversionOptions,
    val convertAndDeleteOriginal: Boolean = true,
    val createdAt: Long,
    val error: String,
)

@Serializable
enum class ConversionStatus {
    QUEUED,
    RUNNING,
}

@Serializable
data class ConversionQueueEntry(
    val id: String,
    val sourceId: String,
    val sourceTitle: String,
    val mode: ConversionMode,
    val options: ConversionOptions,
    val convertAndDeleteOriginal: Boolean = true,
    val status: ConversionStatus,
    val createdAt: Long,
)

@Serializable
data class ConversionQueueSnapshot(
    val queued: List<ConversionQueueEntry>,
    val running: ConversionQueueEntry?,
    val problematic: List<ProblematicConversionTask>,
)

@Serializable
data class ConversionSourceSummary(
    val sourceId: String,
    val alreadyConverted: Boolean,
    val queued: List<ConversionQueueEntry>,
    val running: ConversionQueueEntry?,
    val problematic: List<ProblematicConversionTask>,
)

@Serializable
data class ConversionBatchSummary(
    val total: Int,
    val enqueued: Int,
    val skipped: Int,
    val missing: Int,
)
