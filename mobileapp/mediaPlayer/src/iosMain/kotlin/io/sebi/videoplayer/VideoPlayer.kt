package io.sebi.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState,
    implementationMapping: ImplementationMapping
) {
    println(implementationMapping)
    when(implementationMapping[Ios]) {
        AVKit, null -> AVKitPlayer(url, modifier, videoPlayerState)
        VLC -> VLCVideoPlayer(url, modifier, videoPlayerState)
    }
}