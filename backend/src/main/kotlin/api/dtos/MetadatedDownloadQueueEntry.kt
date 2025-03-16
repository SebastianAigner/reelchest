package io.sebi.api.dtos

import dz.jtsgen.annotations.TypeScript
import io.sebi.downloader.DownloadTaskDTO
import kotlinx.serialization.Serializable

@TypeScript
@Serializable
data class MetadatedDownloadQueueEntry(val queueEntry: DownloadTaskDTO, val title: String)