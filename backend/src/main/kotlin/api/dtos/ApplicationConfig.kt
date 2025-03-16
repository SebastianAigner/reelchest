package io.sebi.api.dtos

import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable

@TypeScript
@Serializable
data class ApplicationConfig(
    val development: Boolean,
    val port: Int,
    val shutdownUrl: String,
    val connectionGroupSize: Int,
    val workerGroupSize: Int,
    val callGroupSize: Int,
    val shutdownGracePeriod: Long,
    val shutdownTimeout: Long,
    val requestQueueLimit: Int,
    val runningLimit: Int,
    val responseWriteTimeoutSeconds: Int,
)