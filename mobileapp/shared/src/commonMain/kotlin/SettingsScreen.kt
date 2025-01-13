import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.toSuspendSettings
import com.russhwolf.settings.set

class SettingsScreenModel() : StateScreenModel<SettingsScreenModel.SettingsScreenState>(
    SettingsScreenState(
        ""
    )
) {
    data class SettingsScreenState(
        val foo: String
    )

    @OptIn(ExperimentalSettingsApi::class)
    val settings = Settings().toSuspendSettings()

}

object SettingsScreen : Screen {
    val settings = Settings()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var isVlc by remember { mutableStateOf(settings.getBoolean("vlc", false)) }
        LaunchedEffect(isVlc) {
            settings["vlc"] = isVlc
        }
        Box(Modifier.fillMaxSize()) {
            Column {
                Row {
                    Checkbox(isVlc, onCheckedChange = { isVlc = it })
                    Text("Use VLC")
                }
                Row {
                    var snippetEndpoint by remember { mutableStateOf(settings.getString("snippetEndpoint", "")) }
                    Text("Snippet Endpoint: ")
                    TextField(value = snippetEndpoint, onValueChange = { snippetEndpoint = it })
                    Button(onClick = { settings.putString("snippetEndpoint", snippetEndpoint) }) { Text("Set") }
                }
                Button(onClick = { navigator.pop() }) {
                    Text("Back")
                }
                Button(onClick = {
                    settings.set("endpoint", "INVALID")
                }) {
                    Text("Invalidate Endpoint")
                }
            }
        }
    }
}