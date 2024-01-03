import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

object SetupScreen : Screen {
    val settings = Settings()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var configuration by remember { mutableStateOf(settings.get<String>("endpoint")) }
        var isValid by remember { mutableStateOf(false) }
        LaunchedEffect(isValid) {
            if (isValid) {
                settings.putString("endpoint", configuration!!)
                navigator.push(VideoListScreen(navigator.toMyNavigator()))
            }
        }
        LaunchedEffect(configuration) {
            configuration?.let {
                try {
                    val canAccessLogsEndpoint = globalHttpClient.get(it + "/api/log")
                        .status
                    isValid = canAccessLogsEndpoint == HttpStatusCode.OK
                } catch (e: Exception) {
                    println("Oh no! $e")
                }
            }
        }
        Column {
            Text("Enter the URL of your Reelchest server")
            TextField(configuration ?: "", onValueChange = {
                configuration = it
            })
            Button(onClick = {
                navigator.push(TikTokScreen())
            }) {
                Text("Go directly to TikTok")
            }
            Button(onClick = {
                navigator.push(WindowManaScreen())
            }) {
                Text("Go to WM")
            }
        }
    }
}