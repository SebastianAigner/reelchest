package io.sebi.windowmanager

import WindowManager
import XPWindow
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import myapplication.windowmanager.generated.resources.Res
import myapplication.windowmanager.generated.resources.bliss
import myapplication.windowmanager.generated.resources.exe
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun Launcher(wm: WindowManager, icons: List<Pair<String, () -> XPWindow>>) {
    val background = painterResource(Res.drawable.bliss)

    Image(
        background,
        null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize()
    )

    Box(Modifier.padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            for ((title, windowCreator) in icons) {
                LauncherIcon(text = title) {
                    wm.spawnWindow(windowCreator())
                }
            }
        }
    }
}
//
@OptIn(ExperimentalResourceApi::class)
@Composable
fun LauncherIcon(image: Painter = painterResource(Res.drawable.exe), text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(image, null, Modifier.size(45.dp).clickable { onClick() })
        Text(text, color = Color.White)
    }
}