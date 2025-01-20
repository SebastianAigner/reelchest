package io.sebi.webview

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun BrowserView(
    url: String,
    modifier: Modifier,
) {
    // Adding a WebView inside AndroidView
    // with layout as full screen
    AndroidView(modifier = modifier, factory = {
        WebView(it).apply {
            this.webViewClient = WebViewClient()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.settings.javaScriptEnabled = true
            this.settings.domStorageEnabled = true
            this.isVerticalScrollBarEnabled = true
            this.settings.builtInZoomControls = true
        }
    }, update = {
        it.webViewClient = WebViewClient()
        it.loadUrl(url)
    })
}