package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.config.AppConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

suspend fun RoutingContext.mediaLibraryDiskUsageHandler() {
    val path = File(AppConfig.mediaLibraryPath)
    val totalBytes = path.totalSpace
    val freeBytes = path.freeSpace
    val usableBytes = path.usableSpace

    call.respond(buildJsonObject {
        put("path", path.absolutePath)
        put("totalBytes", totalBytes)
        put("freeBytes", freeBytes)
        put("usableBytes", usableBytes)
    })
}
