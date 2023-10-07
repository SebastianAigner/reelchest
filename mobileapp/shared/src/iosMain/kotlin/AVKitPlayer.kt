import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
fun AVKitPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState
) {
    val uiView = remember { UIView() }
    val avPlayerLayer = remember { AVPlayerLayer() }
    val mediaPlayer = remember {
        uiView.layer.addSublayer(avPlayerLayer)
        AVPlayer(NSURL.URLWithString(url)!!).apply {
            avPlayerLayer.player = this
        }
    }

    videoPlayerState.controlledPlayer = object : ControllableVideoPlayer {
        override fun play() {
            mediaPlayer.play()
        }

        override fun stop() {
            mediaPlayer.pause()
        }

        override fun pause() {
            mediaPlayer.pause()
        }

        override fun jumpBackward(seconds: Int) {
            val currSeconds = CMTimeGetSeconds(mediaPlayer.currentTime()) // TODO: This seems like unnecessary rounding
            val newTime = (currSeconds - seconds).coerceAtLeast(0.0)
            val cmTime = CMTimeMake(((newTime * 1000).toLong()), 1000)
            mediaPlayer.seekToTime(cmTime)
        }

        override fun jumpForward(seconds: Int) {
            val currSeconds = CMTimeGetSeconds(mediaPlayer.currentTime()) // TODO: This seems like unnecessary rounding
            val newTime = currSeconds + seconds
            val cmTime = CMTimeMake(((newTime * 1000).toLong()), 1000)
            mediaPlayer.seekToTime(cmTime)
        }

        override fun setPosition(f: Float) {
            // TODO: I assume this doesn't work for live content, and should probably be handled for streaming media accordingly.
            // Further investigation required.
            val dur = mediaPlayer.currentItem?.duration ?: return
            val totalSeconds = CMTimeGetSeconds(dur)
            val partialSeconds = totalSeconds * f
            val cmTime = CMTimeMake((partialSeconds * 1000).toLong(), 1000)
            mediaPlayer.seekToTime(cmTime)
        }
    }


    Box(Modifier.fillMaxSize()) {
        UIKitView(
            factory = {
                uiView
            },
            update = {
                // View's been inflated or state read in this block has been updated
                mediaPlayer.play()
            },
            onResize = { view, rect ->
                avPlayerLayer.setFrame(rect)
                view.setFrame(rect)
            },
            onRelease = {
                mediaPlayer.pause()
                mediaPlayer.cancelPendingPrerolls()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}