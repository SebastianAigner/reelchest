import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import cafe.adriel.voyager.core.screen.Screen
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource

// Let me add a superclass!
class TikTokScreen : Screen {
    val demoUrls = listOf(
        "https://v.redd.it/j5fx92kracmb1",
        "https://v.redd.it/u6fvhu26lmnb1",
        "https://v.redd.it/ml4zw05m5ynb1",
        "https://v.redd.it/zst7n5ki18rb1",
        "https://v.redd.it/fiurgvgypnnb1"
    )
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val pagerState = rememberPagerState(pageCount = { demoUrls.size })
        VerticalPager(state = pagerState) { page ->
            val videoPlayerState = rememberVideoPlayerState()

            LaunchedEffect(pagerState.isScrollInProgress) {
                if(pagerState.isScrollInProgress) {
                    videoPlayerState.pause()
                } else {
                    if(pagerState.currentPage == page) {
                        videoPlayerState.play()
                    }
                }
            }

            TikTokPlayer(demoUrls[page], videoPlayerState)
        }
    }
}

@Composable fun TikTokPlayer(url: String, videoPlayerState: VideoPlayerState) {
    // Our page content
    println("Composing stuff!")

    Column {
        Text(
            text = url,
            modifier = Modifier.fillMaxWidth()
        )
        Box {
            VideoPlayer(url + "/HLSPlaylist.m3u8", Modifier.fillMaxSize(), videoPlayerState)
            Box(Modifier.fillMaxSize().clickable { // TODO: I'm trying to prevent touch events from getting forwarded to the underlying, potentially native, video player view.
                if(videoPlayerState.isPlaying.value) {
                    videoPlayerState.pause()
                } else {
                    videoPlayerState.play()
                }
            }) {
                
            }
        }
    }
}