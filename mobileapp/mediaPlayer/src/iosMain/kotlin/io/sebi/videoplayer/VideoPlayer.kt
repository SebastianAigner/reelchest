package io.sebi.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState,
) {
    if (false) { // todo: provide a better selection mechanism
        VLCVideoPlayer(url, modifier, videoPlayerState)
    } else {
        AVKitPlayer(url, modifier, videoPlayerState)
    }
}