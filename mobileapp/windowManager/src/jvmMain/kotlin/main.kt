import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main(): Unit = application {
    Window(onCloseRequest = ::exitApplication, title = "Doors") {

        val wm = remember { WindowManager() }
        LaunchedEffect(wm) {
            wm.spawnWindow(ExampleXPWindow(wm = wm))
        }
//        Launcher(wm)
        wm.UI()
    }
}
