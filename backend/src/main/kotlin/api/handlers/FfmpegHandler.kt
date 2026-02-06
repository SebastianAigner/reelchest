package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.ffmpeg.globalFfmpegMutex
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun RoutingContext.ffmpegMutexHandler() {
    val locked = globalFfmpegMutex.isLocked
    val lockedAt = if (locked) globalFfmpegMutex.lockedAt.toEpochMilliseconds() else null
    val lockedLabel = if (locked) globalFfmpegMutex.lockedLabel else null

    call.respond(
        buildJsonObject {
            put("locked", locked)
            if (lockedAt != null) {
                put("lockedAt", lockedAt)
            }
            if (!lockedLabel.isNullOrBlank()) {
                put("label", lockedLabel)
            }
        }
    )
}
