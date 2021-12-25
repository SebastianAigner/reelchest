package io.sebi.downloader

import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class DownloadTaskDTO(override val originUrl: String, val progress: Double) : WithOriginUrl {
    companion object {
        fun from(dt: DownloadTask): DownloadTaskDTO {
            return DownloadTaskDTO(dt.originUrl, dt.progress)
        }

        fun from(problematicTask: ProblematicTask): DownloadTaskDTO {
            return DownloadTaskDTO(problematicTask.originUrl, 0.0)
        }
    }
}

data class DownloadTask(
    override val originUrl: String,
    val getDirectDownloadUrls: suspend () -> List<String>,
    val onPostProcess: suspend (List<File>) -> File,
    val onComplete: suspend (CompletedDownloadTask) -> Unit,
    var progress: Double = 0.0
) : WithOriginUrl {
    override fun toString(): String {
        return "Task[$originUrl]"
    }
}

data class ProblematicTask(
    override val originUrl: String,
    val error: Throwable
) : WithOriginUrl

@TypeScript
@Serializable
data class ProblematicTaskDTO(
    override val originUrl: String,
    val error: String
) : WithOriginUrl {
    companion object {
        fun from(problematicTask: ProblematicTask): ProblematicTaskDTO {
            return ProblematicTaskDTO(problematicTask.originUrl, problematicTask.error.toString())
        }
    }
}

data class CompletedDownloadTask(
    val targetFile: File,
    override val originUrl: String,
) : WithOriginUrl

interface WithOriginUrl {
    val originUrl: String
}