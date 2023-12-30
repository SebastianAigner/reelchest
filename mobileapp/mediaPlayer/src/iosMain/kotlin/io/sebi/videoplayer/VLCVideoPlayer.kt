package io.sebi.videoplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.MobileVLCKit.VLCMedia
import cocoapods.MobileVLCKit.VLCMediaPlayer
import cocoapods.MobileVLCKit.VLCMediaPlayerDelegateProtocol
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSNotification
import platform.Foundation.NSURL
import platform.UIKit.UIView
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class MediaPlayerDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
    override fun mediaPlayerTimeChanged(aNotification: NSNotification?) {
        println(aNotification)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
fun VLCVideoPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState
) {
    val uiView = remember { UIView() }
    val mediaPlayer = remember {
        VLCMediaPlayer().apply {
            this.drawable = uiView
            this.media = VLCMedia.mediaWithURL(NSURL.URLWithString(url)!!)
            this.delegate = MediaPlayerDelegate()
        }
    }


    videoPlayerState.controlledPlayer = object : ControllableVideoPlayer {
        override fun play() = mediaPlayer.play()

        override fun stop() = mediaPlayer.stop()

        override fun pause() = mediaPlayer.pause()

        override fun jumpBackward(seconds: Int) = mediaPlayer.jumpBackward(seconds)

        override fun jumpForward(seconds: Int) = mediaPlayer.jumpForward(seconds)

        override fun setPosition(f: Float) = mediaPlayer.setPosition(f)

    }


    LaunchedEffect(Unit) {
        while (true) {
            delay(32) // TODO: There is a MediaPlayerDelegate (see above), but from quick tests, I'm not sure how reliable it is.
            videoPlayerState.position.value = mediaPlayer.position
        }
    }
    Box(Modifier.fillMaxSize()) {
        UIKitView(
            factory = { uiView },
            update = { mediaPlayer.play() },
            onRelease = {
                mediaPlayer.stop()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}