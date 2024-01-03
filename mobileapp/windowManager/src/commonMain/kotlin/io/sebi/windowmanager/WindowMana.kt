import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random


abstract class XPWindow(
    val id: String = Random.nextInt().toString(),
    val wm: WindowManager,
    val title: String = "Untitled"
) {
    val windowScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { coroutineContext, throwable ->
            wm.spawnWindow(object : XPWindow(wm = wm, title = "Error") {
                @Composable
                override fun Content() {
                    Text(throwable.stackTraceToString())
                }
            })
        })

    @Composable
    abstract fun Content()

    open fun onDispose() {
        windowScope.cancel()
    }
}

class JSONBrowserWindow(wm: WindowManager) : XPWindow(wm = wm, title = "JSON Browser") {
    @Composable
    override fun Content() {
        var text by remember { mutableStateOf("") }
        var res by remember { mutableStateOf("") }
        LaunchedEffect(text) {
            try {

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Column(Modifier.fillMaxSize()) {
            TextField(text, { text = it })
            Text(res)
        }
    }

}

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

@Composable
fun XPButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clickable { onClick() }.shadow(2.dp).border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            .background(Color(0xFFF4F4F0)).padding(10.dp)
    ) {
        Text(text)
    }
}

class WindowManager {
    val windows = mutableStateListOf<XPWindow>()
    val zIndices = mutableStateMapOf<XPWindow, Float>()
    val locations = mutableMapOf<XPWindow, MutableState<DpOffset>>()

    fun spawnWindow(xpWindow: XPWindow) {
        exitFullScreen()
        windows += xpWindow
        focusWindow(xpWindow)
    }

    fun closeWindow(xpWindow: XPWindow) {
        xpWindow.onDispose()
        windows -= xpWindow
    }

    fun focusWindow(xpWindow: XPWindow) {
        zIndices[xpWindow] = (zIndices.maxOfOrNull { it.value } ?: 0.0f) + 1.0f
    }

    var fullscreenedWindow by mutableStateOf<XPWindow?>(null)

    fun setFullScreen(xpWindow: XPWindow) {
        fullscreenedWindow = xpWindow
    }

    fun exitFullScreen() {
        fullscreenedWindow = null
    }

    @Composable
    fun Windows() {
        for (window in windows) {
            key(window.id) {
                MyWindow(
                    640.dp,
                    480.dp,
                    locations.getOrPut(window) { mutableStateOf(DpOffset(20.dp, 20.dp)) }.value,
                    zIndex = zIndices[window] ?: 0.0f,
                    isFullScreen = window === fullscreenedWindow,
                    onDrag = {
                        locations[window]!!.value += it
                    },
                    onFocus = {
                        focusWindow(window)
                    },
                    closeWindow = { closeWindow(window) },
                    onRequestFullscreen = {
                        if (fullscreenedWindow === window) {
                            exitFullScreen()
                        } else {
                            setFullScreen(window)
                        }
                    },
                    title = window.title
                ) {
                    window.Content()
                }
            }
        }
    }

    @Composable
    fun UI() {
        Box(Modifier.fillMaxSize()) {
            Windows()
        }
    }
}

interface WindowCapableNavigator<T> {
    fun goBack()
    fun goForward(screen: T)
    fun goNewWindow(screen: T)
}

@Composable
fun MyWindow(
    width: Dp,
    height: Dp,
    offset: DpOffset,

    zIndex: Float,
    isFullScreen: Boolean,
    onDrag: (DpOffset) -> Unit,
    onFocus: () -> Unit,
    onRequestFullscreen: () -> Unit,
    closeWindow: () -> Unit,
    title: String,
    content: @Composable () -> Unit = {}
) {
//    var offsetX by remember { mutableStateOf(x) }
//    var offsetY by remember { mutableStateOf(y) }
    var currentWidth by remember { mutableStateOf(width) }
    var currentHeight by remember { mutableStateOf(height) }
    var expanded by remember { mutableStateOf(false) }
    var shouldResizeX by remember { mutableStateOf(false) }
    var shouldResizeY by remember { mutableStateOf(false) }
    Box(Modifier
        .zIndex(zIndex)
        .then(if (!isFullScreen) Modifier.offset(offset.x, offset.y) else Modifier)
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                onRequestFullscreen()
            }, onPress = {
                onFocus()
            })
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    onFocus()
                    if (offset.x.toDp() > currentWidth - 10.dp) {
                        shouldResizeX = true
                    }
                    if (offset.y.toDp() > currentHeight - 10.dp) {
                        shouldResizeY = true
                    }
                },
                onDragEnd = {
                    shouldResizeX = false
                    shouldResizeY = false
                },
                onDrag = { change, dragAmount ->
                    if (shouldResizeX) {
                        change.consume()
                        currentWidth += dragAmount.x.toDp()
                    }
                    if (shouldResizeY) {
                        change.consume()
                        currentHeight += dragAmount.y.toDp()
                    }
                }
            )
        }
        .shadow(10.dp)
        .then(if (!isFullScreen) Modifier.border(3.dp, Color(0xFF0956EE)).padding(3.dp) else Modifier)
        .background(Color(0xFFEBE8D6))
        .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.width(currentWidth).height(currentHeight))
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
            // Top Window Bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color(0xFF0956EE))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                onFocus()
                                change.consume()
                                onDrag(DpOffset(dragAmount.x.toDp(), dragAmount.y.toDp()))
                            }
                        )
                    }, contentAlignment = Alignment.CenterStart
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        title,
                        fontSize = 1.2.em,
                        color = Color.White,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    Box(Modifier.clickable {
                        closeWindow()
                    }.background(Color.Red).aspectRatio(1.0f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text("X", color = Color.White)
                    }
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun Launcher(wm: WindowManager, icons: List<Pair<String, () -> XPWindow>>) {
    val background = painterResource(DrawableResource("bliss.png"))

    Image(background, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

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

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LauncherIcon(
    image: Painter = painterResource(DrawableResource("exe.jpeg")),
    text: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(image, null, Modifier.size(45.dp).clickable { onClick() })
        Text(text, color = Color.White)
    }
}