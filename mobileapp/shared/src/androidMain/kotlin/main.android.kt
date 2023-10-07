import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

actual fun getPlatformName(): String = "Android"

@Composable
fun MainView() = App()