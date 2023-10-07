import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.russhwolf.settings.Settings

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    videoPlayerState: VideoPlayerState
) {
    val s = remember { Settings() }
    if (s.getBoolean("vlc", true)) {
        VLCVideoPlayer(url, modifier, videoPlayerState)
    } else {
        AVKitPlayer(url, modifier, videoPlayerState)
    }
}