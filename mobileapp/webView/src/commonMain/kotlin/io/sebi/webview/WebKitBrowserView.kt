package io.sebi.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun WebKitBrowserView(
    url: String,
    modifier: Modifier,
): Unit