package io.sebi.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL.Companion.URLWithString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.WebKit.WKWebView

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun WebKitBrowserView(
    url: String,
    modifier: Modifier,
    ) {
    println("Browser view with URL $url")
    androidx.compose.ui.viewinterop.UIKitView(
        factory = {
            val req = NSMutableURLRequest().apply {
                setURL(URLWithString(url))
            }
            WKWebView().apply {
                loadRequest(req)
            }
        },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
    )
}