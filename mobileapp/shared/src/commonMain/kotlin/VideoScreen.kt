import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.sebi.videoplayer.VideoPlayer
import io.sebi.videoplayer.VideoPlayerState
import io.sebi.videoplayer.rememberVideoPlayerState
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
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black), // TODO: This "Black" is Placebo: It seems the Color doesn't actually punch through when not set in the AVPlayer. Report this.
            contentAlignment = Alignment.BottomStart
        ) {
            VideoPlayer(
                url = videoUrl,
                Modifier.fillMaxSize().background(Color.Black),
                videoPlayerState
            )
            var pos by remember { mutableStateOf<Offset>(Offset(0.0f, 0.0f)) }
            Box(Modifier.fillMaxSize().pointerInput (Unit) {
                detectTapGestures(onTap = {
                    println("Tapped $it")
                    shouldShowUI = !shouldShowUI
                    pos = it
                })
            }) {

            }
            if (shouldShowUI) {
                
                Box(Modifier.fillMaxSize()) {
                    StickyControlPanel(pos, { shouldShowUI = !shouldShowUI }, listOf("-10", "P/P", "+10"), {
                        when(it) {
                            0 -> videoPlayerState.jumpBackward(10)
                            1 -> if(videoPlayerState.isPlaying.value) videoPlayerState.pause() else videoPlayerState.play()
                            2 -> videoPlayerState.jumpForward(10)
                            else -> println("huh?")
                        }
                    }, videoPlayerState.position.value, {
                        videoPlayerState.setPosition(it)
                    })
                    VideoPageControls(videoPlayerState)
                }
                Row {
                    Button(onClick = {
                        navigator.pop()
                    }) {
                        Text("Back")
                    }
                    cta?.invoke()
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


@Composable
fun StickyControlPanel(pos: Offset, onMiddlePress: () -> Unit, topRowLabels: List<String>, onTopRowPressed: (Int) -> Unit, sliderValue: Float, onSliderValueChanged: (Float) -> Unit) {
    with(LocalDensity.current) {
        val CENTER_LEN = 50.dp
        val centerShift = DpOffset(CENTER_LEN / 2, CENTER_LEN / 2)
        val centerPoint = DpOffset(pos.x.toDp(), pos.y.toDp()) - centerShift
        val centerElementTopLeft = DpOffset(pos.x.toDp(), pos.y.toDp())
        val topRowOffset = centerElementTopLeft + DpOffset(-CENTER_LEN, -CENTER_LEN) - centerShift
        val bottomRowOffset = centerElementTopLeft + DpOffset(-CENTER_LEN * 2, CENTER_LEN) - centerShift

        val ROW_WIDTH = CENTER_LEN * 3
        val ROW_HEIGHT = CENTER_LEN
        println("offset $topRowOffset")
        Row(
            Modifier.absoluteOffset(topRowOffset.x, topRowOffset.y).width(ROW_WIDTH).height(ROW_HEIGHT)
                .background(Color.Blue)
        ) {
            repeat(3) {
                Box(modifier = Modifier.width(CENTER_LEN).height(CENTER_LEN).clickable { onTopRowPressed(it) }) {
                    Text(topRowLabels.getOrNull(it) ?: "", color = Color.White)
                }
            }
        }
        Row(
            Modifier.absoluteOffset(bottomRowOffset.x, bottomRowOffset.y).width(CENTER_LEN * 5).height(ROW_HEIGHT)
                .background(Color.Blue)
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { onSliderValueChanged(it) },
                value = sliderValue,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
        }
        Box(
            Modifier.absoluteOffset(centerPoint.x, centerPoint.y).width(50.dp).height(50.dp)
                .background(Color.Red).clickable { onMiddlePress() }
        )
    }

}