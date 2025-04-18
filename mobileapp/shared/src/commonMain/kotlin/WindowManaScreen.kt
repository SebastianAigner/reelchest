import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.sebi.webview.BrowserView
import io.sebi.windowmanager.ExampleXPWindow
import io.sebi.windowmanager.Launcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun WindowManager.toMyNavigator(): WindowCapableNavigator<Screen> {
    val wm = this
    return object : WindowCapableNavigator<Screen> {
        override fun goBack() {

        }

        override fun goNewWindow(screen: Screen) {
            wm.spawnWindow(screen.toXpWindow(wm))
        }

        override fun goForward(screen: Screen) {
            TODO("Not yet implemented")
        }

    }
}

class WindowManaScreen() : Screen {

    val wm = WindowManager()

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        LaunchedEffect(wm) {
            wm.spawnWindow(ExampleXPWindow(wm = wm))
        }
        Launcher(
            wm, listOf(
                "Web" to {
                    object : XPWindow(wm = wm, title = "web.exe") {
                        @Composable
                        override fun Content() {
                            Box(Modifier.padding(5.dp)) {
                                BrowserView("https://old.reddit.com", Modifier.fillMaxSize())
                            }
                        }
                    }
                },
                "Videos" to {
                    val vwindow = VideoListScreen(navigator = wm.toMyNavigator()).toXpWindow(wm)
                    GlobalScope.launch { // TODO: No more globalscope, please :)
                        delay(500)
                        wm.setFullScreen(vwindow)
                    }
                    vwindow
                },
                "Downloads" to {
                    DownloadsScreen().toXpWindow(wm)
                },
                "Search" to {
                    SearchScreen(wm.toMyNavigator()).toXpWindow(wm)
                },
                "Logs" to {
                    LogWindow(wm)
                },
                "Close" to {
                    currentNavigator.pop()
                    LogWindow(wm) // todo: this is a hack because our icons are expected to return an XPWindow, but here we just want to leave.
                }
            )
        )
        wm.UI()
    }
}

fun Screen.toXpWindow(wm: WindowManager): XPWindow {
    val screen = this
    return object : XPWindow(wm = wm, title = this::class.simpleName.toString()) {
        @Composable
        override fun Content() {
            screen.Content()
        }
    }
}