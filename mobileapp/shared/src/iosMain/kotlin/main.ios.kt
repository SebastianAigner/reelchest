import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL.Companion.URLWithString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.WebKit.WKWebView

actual fun getPlatformName(): String = "iOS"

fun MainViewController() = ComposeUIViewController { App() }


@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun WebKitBrowserView(
    url: String,
    modifier: Modifier,
) {

    val wkView = remember {
        WKWebView()
    }
    LaunchedEffect(url) {
        val req = NSMutableURLRequest().apply {
            setURL(URLWithString(url))
        }
        wkView.loadRequest(req)
    }

    Box(Modifier.fillMaxSize()) {
        UIKitView(
            factory = {
                wkView
            },
            update = {
                // View's been inflated or state read in this block has been updated
                // maybe navigate here?
            },
            onResize = { view, rect ->
                view.setFrame(rect)
            },
            onRelease = {

            },
            modifier = Modifier.fillMaxSize()
        )
    }

}