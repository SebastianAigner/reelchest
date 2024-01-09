import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

class LogWindow(wm: WindowManager) : XPWindow(title = "Logs", wm = wm) {
    @Composable
    override fun Content() {
        var contents by remember { mutableStateOf<List<LogMessage>>(emptyList()) }
        LaunchedEffect(Unit) {
            while (true) {
                val messages = globalHttpClient.get("http://192.168.178.165:8080/api/log").body<List<LogMessage>>()
                contents = messages
                delay(500)
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(state = rememberScrollState())) {
            for (line in contents) {
                Text(line.formattedMessage, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Serializable
data class LogMessage(
    val formattedMessage: String
)