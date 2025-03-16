package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.api.dtos.ApplicationConfig

/**
 * Handler for getting the application configuration.
 */
suspend fun RoutingContext.configHandler() {
    val env = call.application.environment
    val config = env.config

    call.respond(
        ApplicationConfig(
            development = call.application.developmentMode, // TODO: Ktor 3 API?
            port = config.property("ktor.deployment.port").getString().toInt(),
            shutdownUrl = config.property("ktor.deployment.shutdown.url").getString(),
            connectionGroupSize = config
                .propertyOrNull("ktor.deployment.connectionGroupSize")
                ?.getString()
                ?.toInt() ?: Runtime.getRuntime().availableProcessors() * 2,
            workerGroupSize = config.propertyOrNull("ktor.deployment.workerGroupSize")?.getString()?.toInt()
                ?: Runtime.getRuntime().availableProcessors() * 2,
            callGroupSize = config.propertyOrNull("ktor.deployment.callGroupSize")?.getString()?.toInt()
                ?: Runtime.getRuntime().availableProcessors() * 2,
            shutdownGracePeriod = config
                .propertyOrNull("ktor.deployment.shutdownGracePeriod")
                ?.getString()
                ?.toLong() ?: 1000,
            shutdownTimeout = config.propertyOrNull("ktor.deployment.shutdownTimeout")?.getString()?.toLong()
                ?: 5000,
            requestQueueLimit = config.propertyOrNull("ktor.deployment.requestQueueLimit")?.getString()?.toInt()
                ?: 16,
            runningLimit = config.propertyOrNull("ktor.deployment.runningLimit")?.getString()?.toInt() ?: 10,
            responseWriteTimeoutSeconds = config
                .propertyOrNull("ktor.deployment.responseWriteTimeoutSeconds")
                ?.getString()
                ?.toInt() ?: 10
        )
    )
}