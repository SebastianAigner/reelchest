package io.sebi.windowmanager

import WindowManager
import XPWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExampleXPWindow(wm: WindowManager, title: String = "Untitled") :
    XPWindow(wm = wm, title = title) {

    @Composable
    override fun Content() {
        var x by remember { mutableStateOf(0) }
        Column {
            Text(title)
            Button(onClick = {
                wm.spawnWindow(ExampleXPWindow(wm = wm, title = title + "a"))
            }) {
                Text("Spawn Window")
            }
            XPButton("Count! $x") {
                windowScope.launch {
                    while (true) {
                        x++
                        delay(500)
                    }
                }
            }
            XPButton("Crash!", onClick = {
                windowScope.launch {
                    error("Uncaught!")
                }
            })
            XPButton("Cancel!", onClick = {
                windowScope.launch {
                    windowScope.cancel()
                }
            })
            XPButton("Full!", onClick = {
                wm.setFullScreen(this@ExampleXPWindow)
            })
        }
    }
}