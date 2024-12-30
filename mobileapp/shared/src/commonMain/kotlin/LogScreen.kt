import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get

class LogWindow(wm: WindowManager) : XPWindow(title = "Logs", wm = wm) {
    @Composable
    override fun Content() {
        var contents by remember { mutableStateOf<List<LogMessage>>(emptyList()) }
        LaunchedEffect(Unit) {
            while (true) {
                val messages =
                    globalHttpClient.get(Settings().get<String>("endpoint")!! + "/api/log").body<List<LogMessage>>()
                contents = messages
                delay(500)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(contents) { line ->
                Text(line.formattedMessage, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Serializable
data class LogMessage(
    val formattedMessage: String
)