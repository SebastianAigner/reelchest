import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
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
import kotlin.random.Random

@Composable
fun GenericImageCell(imageUrl: String, title: String, onClick: () -> Unit) {
    var shouldDrawImage by remember { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        // TODO: This feels like a hack -- probably, this "fast scrolling" + cancellation should be handled by the imageloading library.
        shouldDrawImage = false
        delay(200)
        println("Should show image now!")
        shouldDrawImage = true
    }
    Column(Modifier
        .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
        .clickable {
            onClick()
        }
        .fillMaxWidth()) {
        if (shouldDrawImage) {
            println("composing with kamel image!")
            KamelImage(
                modifier = Modifier.aspectRatio(1.0f),
                contentDescription = null,
                resource = asyncPainterResource(imageUrl),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.aspectRatio(1.0f).fillMaxSize())
        }
        Text(title)
    }
}

@Composable
fun MediaLibraryEntryCell(entry: MediaLibraryEntry, onClick: () -> Unit) {
    GenericImageCell(
        Settings().get<String>("endpoint")!! + "/api/mediaLibrary/${entry.id}/randomThumb", // todo: this should be a CompositionLocal
        (entry.name.lines().firstOrNull() ?: "No name").take(255),
        onClick = onClick
    )
}

sealed class Sorting(val name: String) {
    abstract fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry>
}

class LatestFirst : Sorting("Latest") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos
    }

}

class OldestFirst : Sorting("Oldest") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos.reversed()
    }

}

class MostViewed : Sorting("Most Viewed") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos.sortedByDescending { it.hits }
    }
}

class LeastViewed : Sorting("Least Viewed") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos.sortedBy { it.hits }
    }

}

class Shuffle(val seed: Int) : Sorting("Shuffled") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        val r = Random(seed)
        return videos.shuffled(r)
    }
}

object VideoListScreen : Screen {
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { VideoListScreenModel() }
        val state by screenModel.state.collectAsState()
        var searchQuery by remember { mutableStateOf("") }
        val sorting = remember { mutableStateOf(screenModel.getAvailableSortingNames().first()) }
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

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.fillMaxSize()) {
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
                        MediaLibraryEntryCell(it) {
                            navigator.push(
                                VideoScreen(
                                    Settings().get<String>("endpoint")!! + "/api/video/${it.id}"
                                )
                            )
                        }
                    }
                }
            }
            FlowRow {
                Button(
                    onClick = { navigator.push(SearchScreen()) },
                ) {
                    Text("Search")
                }
                Button(onClick = { navigator.push(SettingsScreen) }) {
                    Text("Settings")
                }
                Button(onClick = { navigator.push(DownloadsScreen) }) {
                    Text("Downloads")
                }
                Button(onClick = { navigator.push(TikTokScreen()) }) {
                    Text("TikTok")
                }
                val demoUrl = "https://v.redd.it/wvv8jgjksuo71/HLSPlaylist.m3u8?a=1698935975%2CNTQ0ZDUzZWU4NWZjZDdkN2RkOTdiZDhiZGEzMjVmMWNmYTVlOThhMDU2ZjRmMGUzYmI0ZGVlOGMyNDc4MmFkNg%3D%3D&amp;v=1&amp;f=sd"
                Button(onClick = { navigator.push(VideoScreen(demoUrl)) }) {
                    Text("M3U8 Test")
                }
                Button(onClick = { navigator.push(QueueScreen()) }) {
                    Text("Queue")
                }
                Button(onClick = { screenModel.refresh() }) {
                    Text("Refresh")
                }
                Text(state.loadingState)
            }
        }
    }
}