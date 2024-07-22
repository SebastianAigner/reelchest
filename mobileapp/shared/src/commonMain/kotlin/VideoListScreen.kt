import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GenericImageCell(imageUrl: String, title: String, modifier: Modifier) {
    var shouldDrawImage by remember { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        // TODO: This feels like a hack -- probably, this "fast scrolling" + cancellation should be handled by the imageloading library.
        shouldDrawImage = false
        delay(200)
        shouldDrawImage = true
    }
    Column(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .then(modifier)
            .fillMaxWidth()
    ) {
        if (shouldDrawImage) {
            KamelImage(
                modifier = Modifier.aspectRatio(1.0f),
                contentDescription = null,
                resource = asyncPainterResource(imageUrl),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.aspectRatio(1.0f).fillMaxSize())
        }
        Text(title)
    }
}

@Composable
fun MediaLibraryEntryCell(entry: MediaLibraryEntry, modifier: Modifier) {
    GenericImageCell(
        Settings().get<String>("endpoint")!! + "/api/mediaLibrary/${entry.id}/randomThumb", // todo: this should be a CompositionLocal
        (entry.name.lines().firstOrNull() ?: "No name").take(255),
        modifier = modifier
    )
}

class VideoListScreen(val navigator: WindowCapableNavigator<Screen>) : Screen {
    val navigateTo = navigator::goNewWindow

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { VideoListScreenModel() }
        val state by screenModel.state.collectAsState()
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val sorting = rememberSaveable { mutableStateOf(screenModel.getAvailableSortingNames().first()) }
        val scope = rememberCoroutineScope()
        DisposableEffect(searchQuery) {
            val job = scope.launch {
                delay(500)
                screenModel.filter(searchQuery)
            }
            onDispose {
                job.cancel()
            }
        }
        LaunchedEffect(state.loadingState) {
            if ("received" in state.loadingState) {
                delay(2000)
                screenModel.clearState()
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Column {
                Row {
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        }
                    )
                    // TODO: Figure out how a dropdown works
                    for (availableSorting in screenModel.getAvailableSortingNames()) {
                        Button(onClick = { screenModel.setSortingByName(availableSorting) }) {
                            Text("Sort $availableSorting")
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), Modifier.fillMaxSize()
                ) {
                    items(state.filteredVideos, key = { it.id }) {
                        MediaLibraryEntryCell(it, Modifier.combinedClickable(
                            onClick = {
                                navigateTo(
                                    VideoScreen(
                                        Settings().get<String>("endpoint")!! + "/api/video/${it.id}",
                                        navigator
                                    )
                                )
                            },
                            onLongClick = {
                                navigateTo(
                                    WebScreen(
                                        Settings().get<String>("endpoint")!! + "/#/movie/${it.id}"
                                    )
                                )
                            }
                        ))
                    }
                }
            }
            Column {
                FlowRow {
                    Button(
                        onClick = { navigateTo(SearchScreen(navigator)) },
                    ) {
                        Text("Search")
                    }
                    Button(onClick = { navigateTo(SettingsScreen) }) {
                        Text("Settings")
                    }
                    Button(onClick = { navigateTo(DownloadsScreen()) }) {
                        Text("Downloads")
                    }
                    Button(onClick = { navigateTo(TikTokScreen()) }) {
                        Text("TikTok")
                    }
                    Button(onClick = { navigateTo(WindowManaScreen()) }) {
                        Text("XP")
                    }
                    val demoUrl =
                        "https://v.redd.it/wvv8jgjksuo71/HLSPlaylist.m3u8?a=1698935975%2CNTQ0ZDUzZWU4NWZjZDdkN2RkOTdiZDhiZGEzMjVmMWNmYTVlOThhMDU2ZjRmMGUzYmI0ZGVlOGMyNDc4MmFkNg%3D%3D&amp;v=1&amp;f=sd"
                    Button(onClick = { navigateTo(VideoScreen(demoUrl, navigator)) }) {
                        Text("M3U8 Test")
                    }
                    Button(onClick = { navigateTo(QueueScreen()) }) {
                        Text("Queue")
                    }
                    Button(onClick = { screenModel.refresh() }) {
                        Text("Refresh")
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.loadingState.ifBlank { " " }) // Always render space, to keep the line / surrounding buttons from jumping
                    }
                }
            }
        }
    }
}