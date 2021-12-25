package io.sebi.ui.shared

import kotlinx.html.*

private fun FlowOrInteractiveOrPhrasingContent.FastHref(loc: String) {
    a("/$loc") { +loc }
    +" "
}

fun HEAD.commonStyles() {
    styleLink("/static/main.css")
}

fun HTML.commonLayout(title: String, head: (HEAD.() -> Unit)? = null, body: BODY.() -> Unit) {
    head {
        commonStyles()
        head?.invoke(this)
    }
    body {
        h1 { +title }
        topMenu()
        body()
    }
}

fun BODY.topMenu() {
    FastHref("add")
    FastHref("downloads")
    FastHref("upload")
}