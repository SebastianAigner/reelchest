package io.sebi.videoplayer

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState,
    implementationMapping: ImplementationMapping
) {
    // Adds view to Compose

    AndroidView(
        modifier = Modifier.fillMaxSize(), // Occupy the max size in the Compose UI tree
        factory = { context ->
            VideoView(context).apply {
                val videoView = this
                videoPlayerState.controlledPlayer = object : ControllableVideoPlayer {
                    override fun play() {
                        videoView.start()
                    }

                    override fun stop() {
                        videoView.stopPlayback()
                    }

                    override fun pause() {
                        videoView.pause()
                    }

                    override fun jumpBackward(seconds: Int) {
                        if (!videoView.canSeekBackward()) return
                        val newPos = (videoView.currentPosition - seconds * 1000).coerceAtLeast(0)
                        videoView.seekTo(newPos)
                    }

                    override fun jumpForward(seconds: Int) {
                        if (!videoView.canSeekForward()) return
                        val duration = videoView.duration
                        if (duration == -1) {
                            // no duration is available.
                            // TODO: Handle this nicer
                            return
                        }
                        val newPos = (videoView.currentPosition + seconds * 1000).coerceAtMost(duration)
                        videoView.seekTo(newPos)
                    }

                    override fun setPosition(f: Float) {
                        require(f in 0.0..1.0) { "Expected value to be in interval 0.0-1.0!"}
                        if(!videoView.canSeekBackward() || !videoView.canSeekForward()) return
                        val duration = videoView.duration
                        if(duration == -1) {
                            // for live content
                            // TODO: Handle this nicer
                            return
                        }
                        val position = duration * f
                        videoView.seekTo(position.toInt())
                    }

                }
            }
        },
        update = { view ->
            // View's been inflated or state read in this block has been updated
            // Add logic here if necessary
            view.setVideoURI(Uri.parse(url))
            view.start()
        },
    )
}