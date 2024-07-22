package io.sebi.videoplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

interface ControllableVideoPlayer {
    fun play()
    fun stop()
    fun pause()
    fun jumpBackward(seconds: Int)
    fun jumpForward(seconds: Int)

    fun setPosition(f: Float)
}

class VideoPlayerState {
    val position = mutableStateOf(0.0f)
    var controlledPlayer: ControllableVideoPlayer? = null
    fun play() {
        controlledPlayer?.play()
        isPlaying.value = true
    }

    fun stop() {
        controlledPlayer?.stop()
    }

    fun pause() {
        controlledPlayer?.pause()
        isPlaying.value =
            false // TODO: Would love to cover this via the NSNotifications from VLCMediaPlayerDelegateProtocol to make sure its consistent with what the actual player reports.
    }

    fun jumpBackward(seconds: Int) {
        controlledPlayer?.jumpBackward(seconds)
    }

    fun jumpForward(seconds: Int) {
        controlledPlayer?.jumpForward(seconds)
    }

    val isPlaying = mutableStateOf(true)
    fun setPosition(f: Float) {
        controlledPlayer?.setPosition(f)
    }
}

@Composable
fun rememberVideoPlayerState() = remember { VideoPlayerState() }

@Composable
expect fun VideoPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState,
    implementationMapping: ImplementationMapping = ImplementationMapping.DefaultMapping
)