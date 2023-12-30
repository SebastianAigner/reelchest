import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier

actual fun getPlatformName(): String = "Android"

@Composable
fun MainView() = App()

@Composable
actual fun WebKitBrowserView(
    url: String,
    modifier: Modifier,
): Unit = TODO()