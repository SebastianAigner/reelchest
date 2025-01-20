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
import androidx.compose.ui.viewinterop.UIKitView

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun BrowserView(
    url: String,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            WKWebView()
        },
        update = {
            it.loadRequest(NSMutableURLRequest().apply {
                setURL(URLWithString(url))
            })
        },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
    )
}