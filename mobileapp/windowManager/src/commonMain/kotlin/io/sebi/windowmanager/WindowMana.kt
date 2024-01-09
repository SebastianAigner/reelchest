import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost
import kotlin.ranges.rangeTo


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
        Modifier.clickable { onClick() }.shadow(2.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            .background(Color(0xFFF4F4F0)).padding(10.dp)
    ) {
        Text(text)
    }
}

class WindowManager {
    val windows = mutableStateListOf<XPWindow>()
    val zIndices = mutableStateMapOf<XPWindow, Float>()
    val locations = mutableMapOf<XPWindow, MutableState<DpOffset>>()
    val sizes = mutableMapOf<XPWindow, MutableState<DpSize>>()

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
        var desktopSize by remember { mutableStateOf(DpSize.Zero) }
        val localDensity = LocalDensity.current
        Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates: LayoutCoordinates ->
            with(localDensity) {
                val sizeInPixels = coordinates.size
                desktopSize = DpSize(sizeInPixels.width.toDp(), sizeInPixels.height.toDp())
            }
        }) {
            var dragStartOffsetInsideWindowItself by remember {
                mutableStateOf<DpOffset>(DpOffset(0.dp, 0.dp))
            }
            var cursorPositionInDesktop by remember { mutableStateOf(DpOffset.Zero) }
            for (window in windows) {
                key(window.id) {
                    val size =
                        sizes.getOrPut(window) { mutableStateOf(DpSize(640.dp, 480.dp)) }.value
                    MyWindow(
                        width = size.width,
                        height = size.height,
                        locations.getOrPut(window) { mutableStateOf(DpOffset(20.dp, 20.dp)) }.value,
                        zIndex = zIndices[window] ?: 0.0f,
                        isFullScreen = window === fullscreenedWindow,
                        onDragStart = {
                            dragStartOffsetInsideWindowItself = with(localDensity) {
                                DpOffset(it.x.toDp(), it.y.toDp())
                            }
                        },
                        onDrag = {
                            locations[window]!!.value += it
                            cursorPositionInDesktop =
                                locations[window]!!.value + dragStartOffsetInsideWindowItself
                            println("Cursor position on desktop: $cursorPositionInDesktop")
                        },
                        onDragEnd = {
                            // sizes: [10%]---[50%]---[10%]
                            // percentages: 0%-STF%, 25-75% ,(100-STF)% - 100%
                            val shortThirdFactor = 0.02f
                            val firstThirdX = (Float.NEGATIVE_INFINITY.dp)..desktopSize.width * shortThirdFactor
                            val middleThirdX =
                                (desktopSize.width * 0.25f)..(desktopSize.width * 0.75f)
                            val lastThirdX =
                                (desktopSize.width * (1 - shortThirdFactor))..(Float.POSITIVE_INFINITY.dp)

                            val topThirdY = (Float.NEGATIVE_INFINITY.dp)..desktopSize.height * shortThirdFactor
                            val middleThirdY =
                                (desktopSize.height * 0.25f)..(desktopSize.height * 0.75f)
                            val bottomThirdY =
                                (desktopSize.height * (1 - shortThirdFactor))..(Float.POSITIVE_INFINITY.dp)
                            val pos = cursorPositionInDesktop
                            println("$pos $firstThirdX $topThirdY")
                            val snapped: Pair<DpOffset, DpSize>? = when {
                                pos.x in firstThirdX && pos.y in topThirdY -> {
                                    DpOffset(0.dp, 0.dp) to DpSize(
                                        desktopSize.width / 2f,
                                        desktopSize.height / 2f
                                    )
                                }

                                pos.x in firstThirdX && pos.y in middleThirdY -> {
                                    DpOffset(0.dp, 0.dp) to DpSize(
                                        desktopSize.width / 2f,
                                        desktopSize.height
                                    )
                                }

                                pos.x in firstThirdX && pos.y in bottomThirdY -> {
                                    DpOffset(0.dp, desktopSize.height / 2f) to DpSize(
                                        desktopSize.width / 2f,
                                        desktopSize.height / 2f
                                    )
                                }

                                pos.x in lastThirdX && pos.y in topThirdY -> {
                                    DpOffset(desktopSize.width / 2f, 0.dp) to DpSize(
                                        desktopSize.width / 2f,
                                        desktopSize.height / 2f
                                    )
                                }

                                pos.x in lastThirdX && pos.y in middleThirdY -> {
                                    DpOffset(desktopSize.width / 2f, 0.dp) to DpSize(
                                        desktopSize.width / 2f,
                                        desktopSize.height
                                    )
                                }

                                pos.x in lastThirdX && pos.y in bottomThirdY -> {
                                    DpOffset(
                                        desktopSize.width / 2f,
                                        desktopSize.height / 2f
                                    ) to DpSize(desktopSize.width / 2f, desktopSize.height / 2f)
                                }

                                pos.x in middleThirdX && pos.y in topThirdY -> {
                                    DpOffset(0.dp, 0.dp) to DpSize(
                                        desktopSize.width,
                                        desktopSize.height / 2f
                                    )
                                }

                                pos.x in middleThirdX && pos.y in bottomThirdY -> {
                                    DpOffset(0.dp, desktopSize.height / 2f) to DpSize(
                                        desktopSize.width,
                                        desktopSize.height / 2f
                                    )
                                }

                                else -> null
                            }
                            snapped?.let { (offset, size) ->
                                locations[window]!!.value = offset
                                sizes[window]!!.value = size
                            }
                            val loc = locations[window]!!.value
                            val newLoc = DpOffset(
                                loc.x.coerceAtLeast(-sizes[window]!!.value.width * 0.9f)
                                    .coerceAtMost(desktopSize.width - 10.dp),
                                loc.y.coerceAtLeast(0.dp).coerceAtMost(desktopSize.height - 10.dp)
                            )
                            locations[window]!!.value = newLoc
                        },
                        onResize = { x, y ->
                            sizes[window]!!.value += DpSize(x, y)
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
    onDragStart: (Offset) -> Unit,
    onDrag: (change: DpOffset) -> Unit,
    onDragEnd: () -> Unit,
    onResize: (x: Dp, y: Dp) -> Unit,
    onFocus: () -> Unit,
    onRequestFullscreen: () -> Unit,
    closeWindow: () -> Unit,
    title: String,
    content: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var shouldResizeX by remember { mutableStateOf(false) }
    var shouldResizeY by remember { mutableStateOf(false) }
    var hiddenTopBar by remember { mutableStateOf(false) }
    val currentWidth by rememberUpdatedState(width)
    val currentHeight by rememberUpdatedState(height)
    val topBarColor by animateColorAsState(if (hiddenTopBar) Color.Black else Color(0xFF0956EE))
    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            delay(1000)
            hiddenTopBar = true
        } else {
            hiddenTopBar = false
        }
    }
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
                    val xo = offset.x.toDp()
                    val yo = offset.y.toDp()
                    val right = currentWidth - 10.dp
                    val bottom = currentHeight - 10.dp
                    println("$xo (needs $right), $yo (needs $bottom)")
                    if (xo > right) {
                        shouldResizeX = true
                    }
                    if (yo > bottom) {
                        shouldResizeY = true
                    }
                },
                onDragEnd = {
                    shouldResizeX = false
                    shouldResizeY = false
                },
                onDrag = { change, dragAmount ->
                    if (shouldResizeX) {
                        onResize(dragAmount.x.toDp(), 0.dp)
                    }
                    if (shouldResizeY) {
                        onResize(0.dp, dragAmount.y.toDp())
                    }
                    change.consume()
                },
            )
        }
        .shadow(10.dp)
        .then(
            if (isFullScreen) Modifier.fillMaxSize() else Modifier.size(
                width, height
            ),
        )
    ) {
        Box(
            Modifier.fillMaxSize()
                .then(if (!isFullScreen) Modifier.border(10.dp, Color(0xFF0956EE)) else Modifier)
                .then(if (!isFullScreen) Modifier.padding(10.dp) else Modifier)
                .background(Color(0xFFEBE8D6))
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                // Top Window Bar
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
//                    .background(Color(0xFF0956EE))
                        .background(topBarColor)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (isFullScreen) {
                                        return@detectDragGestures
                                    }
                                    onDragStart(offset)
                                },
                                onDrag = { change, dragAmount ->
                                    onFocus()
                                    change.consume()
                                    onDrag(DpOffset(dragAmount.x.toDp(), dragAmount.y.toDp()))
                                },
                                onDragEnd = {
                                    println("Drag end!")
                                    onDragEnd()
                                }
                            )
                        }, contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            title,
                            fontSize = 1.2.em,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                        Box(
                            Modifier.clickable {
                                closeWindow()
                            }.background(Color.Red).aspectRatio(1.0f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("X", color = Color.White)
                        }
                    }
                }
                content()
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun Launcher(wm: WindowManager, icons: List<Pair<String, () -> XPWindow>>) {
    val background = painterResource("bliss.png")

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

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LauncherIcon(image: Painter = painterResource("exe.jpeg"), text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(image, null, Modifier.size(45.dp).clickable { onClick() })
        Text(text, color = Color.White)
    }
}