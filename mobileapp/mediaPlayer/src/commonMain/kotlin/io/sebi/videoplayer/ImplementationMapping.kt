package io.sebi.videoplayer

sealed interface Platform

data object Android : Platform
data object Ios : Platform

sealed interface VideoPlayerImplementation
data object VLC : VideoPlayerImplementation
data object AVKit : VideoPlayerImplementation

data class ImplementationMapping(val mapping: Map<Platform, VideoPlayerImplementation>) :
    Map<Platform, VideoPlayerImplementation> by mapping {
    companion object {
        val DefaultMapping = ImplementationMapping(
            mapOf(
                Ios to AVKit
            )
        )
    }
}