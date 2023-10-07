import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.delay

class VideoScreen(val videoUrl: String, val cta: @Composable (() -> Unit)? = null) : Screen {
    @Composable
    override fun Content() {
        val videoPlayerState = rememberVideoPlayerState()

        val navigator = LocalNavigator.currentOrThrow
        var currentTime by remember { mutableStateOf(0) }
        var shouldShowUI by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(3000)
            shouldShowUI = false
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            VideoPlayer(
                url = videoUrl,
                Modifier.fillMaxSize(),
                videoPlayerState
            )
            Box(Modifier.fillMaxSize().clickable {
                shouldShowUI = !shouldShowUI
            }) {

            }
            if (shouldShowUI) {
                Row {
                    Button(onClick = {
                        navigator.pop()
                    }) {
                        Text("Back")
                    }
                    cta?.invoke()
                }
                Box(Modifier.fillMaxSize()) {
                    VideoPageControls(videoPlayerState)
                }
            }
        }
    }
}

@Composable
fun VideoPageControls(videoPlayerState: VideoPlayerState) {
    Row(Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                videoPlayerState.jumpBackward(10)
            }
        ) {
            Text("-10s")
        }
        Button(
            onClick = {
                videoPlayerState.jumpForward(10)
            }
        ) {
            Text("+10s")
        }
        Button(
            onClick = {
                if (videoPlayerState.isPlaying.value)
                    videoPlayerState.pause()
                else videoPlayerState.play()
            }) {
            Text(
                if (videoPlayerState.isPlaying.value) "Pause" else "Play"
            )
        }
        Slider(
            videoPlayerState.position.value,
            onValueChange = { value ->
                videoPlayerState.setPosition(value)
            }
        )
    }
}