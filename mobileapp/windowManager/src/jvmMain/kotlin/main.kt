import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.sebi.windowmanager.ExampleXPWindow
import io.sebi.windowmanager.Launcher
import java.net.URL

fun main(): Unit = application {
    Window(onCloseRequest = ::exitApplication, title = "Doors") {

        val wm = remember { WindowManager() }
        LaunchedEffect(wm) {
            wm.spawnWindow(ExampleXPWindow(wm = wm))
        }
        Launcher(wm, emptyList())
        wm.UI()
    }
}


class JSONBrowserWindow(wm: WindowManager) : XPWindow(wm = wm, title = "JSON Browser") {
    @Composable
    override fun Content() {
        var text by remember { mutableStateOf("") }
        var res by remember { mutableStateOf("") }
        LaunchedEffect(text) {
            try {
                res = URL(text).readText()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Column(Modifier.fillMaxSize()) {
            TextField(text, { text = it })
            Text(res)
        }
    }

}