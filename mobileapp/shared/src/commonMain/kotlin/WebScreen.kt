import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.sebi.webview.WebKitBrowserView

class WebScreen(val url: String) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            WebKitBrowserView(url, Modifier.fillMaxSize())

            Button({
                currentNavigator.pop()
            }) {
                Text("Back")
            }
        }
    }
}


