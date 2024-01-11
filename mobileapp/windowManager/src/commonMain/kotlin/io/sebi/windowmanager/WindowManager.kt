import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
            val sizeInPixels = coordinates.size
            with(localDensity) {
                desktopSize = DpSize(sizeInPixels.width.toDp(), sizeInPixels.height.toDp())
            }
        }) {
            var dragStartOffsetInsideWindowItself by remember {
                mutableStateOf(DpOffset(0.dp, 0.dp))
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
                            locations.getValue(window).value += it
                            cursorPositionInDesktop =
                                locations.getValue(window).value + dragStartOffsetInsideWindowItself
                        },
                        onDragEnd = {
                            snapWindow(desktopSize, cursorPositionInDesktop, window)
                        },
                        onResize = { op ->
                            when (op) {
                                is BottomRightResizeOperation -> {
                                    sizes.getValue(window).value += DpSize(op.offset.x, op.offset.y)
                                }

                                is TopRightResizeOperation -> {
                                    locations.getValue(window).value += DpOffset(0.dp, op.offset.y)
                                    sizes.getValue(window).value += DpSize(
                                        op.offset.x,
                                        -op.offset.y
                                    )
                                }

                                is TopLeftResizeOperation -> {
                                    locations.getValue(window).value += op.offset
                                    sizes.getValue(window).value += DpSize(
                                        -op.offset.x,
                                        -op.offset.y
                                    )
                                }

                                is BottomLeftResizeOperation -> {
                                    locations.getValue(window).value += DpOffset(op.offset.x, 0.dp)
                                    sizes.getValue(window).value += DpSize(
                                        -op.offset.x,
                                        op.offset.y
                                    )
                                }

                                else -> {
                                    error("Unreachable") // TODO: Why is this necessary?
                                }
                            }
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

    private fun snapWindow(
        desktopSize: DpSize,
        cursorPositionInDesktop: DpOffset,
        window: XPWindow
    ) {
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
        locations[window]!!.value =
            newLoc // TODO: It seems that sometimes, in rare cases, you can move the window out of bounds even with this set. I don't understand why.
    }

    @Composable
    fun UI() {
        Box(Modifier.fillMaxSize()) {
            Windows()
        }
    }
}

sealed interface ResizeOperation
data class TopRightResizeOperation(val offset: DpOffset) : ResizeOperation
data class TopLeftResizeOperation(val offset: DpOffset) : ResizeOperation
data class BottomRightResizeOperation(val offset: DpOffset) : ResizeOperation
data class BottomLeftResizeOperation(val offset: DpOffset) : ResizeOperation


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
    onResize: (ResizeOperation) -> Unit,
    onFocus: () -> Unit,
    onRequestFullscreen: () -> Unit,
    closeWindow: () -> Unit,
    title: String,
    content: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var shouldResizeRight by remember { mutableStateOf(false) }
    var shouldResizeBottom by remember { mutableStateOf(false) }
    var shouldResizeLeft by remember { mutableStateOf(false) }
    var shouldResizeTop by remember { mutableStateOf(false) }
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
                    val right = currentWidth - 10.dp // todo: don't hardcode this
                    val bottom = currentHeight - 10.dp // todo: don't hardcode this
                    val top = 10.dp // todo: don't hardcode this
                    val left = 10.dp // todo: don't hardcode this
                    println("$xo (needs $right), $yo (needs $bottom)")
                    if (xo < left) {
                        shouldResizeLeft = true
                    }
                    if (yo < top) {
                        shouldResizeTop = true
                    }
                    if (xo > right) {
                        shouldResizeRight = true
                    }
                    if (yo > bottom) {
                        shouldResizeBottom = true
                    }
                },
                onDragEnd = {
                    // TODO: Should these be a single state object? (I don't want to make multiple recompositions here)
                    shouldResizeRight = false
                    shouldResizeBottom = false
                    shouldResizeLeft = false
                    shouldResizeTop = false
                },
                onDrag = { change, dragAmount ->
                    when {
                        shouldResizeBottom && shouldResizeRight -> {
                            onResize(
                                BottomRightResizeOperation(
                                    DpOffset(
                                        dragAmount.x.toDp(),
                                        dragAmount.y.toDp()
                                    )
                                )
                            )
                        }

                        shouldResizeTop && shouldResizeRight -> {
                            onResize(
                                TopRightResizeOperation(
                                    DpOffset(
                                        dragAmount.x.toDp(),
                                        dragAmount.y.toDp()
                                    )
                                )
                            )
                        }

                        shouldResizeTop && shouldResizeLeft -> {
                            onResize(
                                TopLeftResizeOperation(
                                    DpOffset(
                                        dragAmount.x.toDp(),
                                        dragAmount.y.toDp()
                                    )
                                )
                            )
                        }

                        shouldResizeBottom && shouldResizeLeft -> {
                            onResize(
                                BottomLeftResizeOperation(
                                    DpOffset(
                                        dragAmount.x.toDp(),
                                        dragAmount.y.toDp()
                                    )
                                )
                            )
                        }

                        shouldResizeTop -> {
                            onResize(TopRightResizeOperation(DpOffset(0.dp, dragAmount.y.toDp())))
                        }

                        shouldResizeLeft -> {
                            onResize(TopLeftResizeOperation(DpOffset(dragAmount.x.toDp(), 0.dp)))
                        }

                        shouldResizeBottom -> {
                            onResize(
                                BottomRightResizeOperation(
                                    DpOffset(
                                        0.dp,
                                        dragAmount.y.toDp()
                                    )
                                )
                            )
                        }

                        shouldResizeRight -> {
                            onResize(
                                BottomRightResizeOperation(
                                    DpOffset(
                                        dragAmount.x.toDp(),
                                        0.dp
                                    )
                                )
                            )
                        }
                    }
                    change.consume()
                    return@detectDragGestures
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

