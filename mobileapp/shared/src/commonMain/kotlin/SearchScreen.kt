import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val thumbUrl: String
)

@Serializable
data class SearchQuery(
    val term: String,
    val offset: Int
)

class SearchScreenModel() : StateScreenModel<SearchScreenModel.SearchScreenState>(
    SearchScreenState(
        emptyList(),
        mostRecentSearch = Settings()["recentsearch"] ?: ""
    )
) {

    data class SearchScreenState(
        val results: List<SearchResult>,
        val offset: Int = 0,
        val currentQuery: String = "",
        val currentSearcher: String = "",
        val mostRecentSearch: String
    )

    fun setSearcher(s: String) {
        mutableState.update {
            it.copy(currentSearcher = s)
        }
    }

    fun search(query: String, searcher: String) {
        Settings().putString("recentsearch", query)
        mutableState.update { it.copy(currentQuery = query, mostRecentSearch = query) }
        screenModelScope.launch {
            val res =
                globalHttpClient.post(Settings().get<String>("endpoint")!! + "/api/search/$searcher") {
                    buildHeaders {
                        contentType(ContentType.Application.Json)
                    }
                    this.setBody(SearchQuery(query, state.value.offset))
                }
            if (res.status == HttpStatusCode.InternalServerError || res.status == HttpStatusCode.BadRequest) {
                println("Attempted to search for $query, but got ${res.status}")
                return@launch
            }
            mutableState.update {
                it.copy(results = res.body())
            }
        }
    }

    fun loadVideoFor(searchResult: SearchResult): String {
        return Settings().get<String>("endpoint")!! + "/decrypt?url=${searchResult.url}"
    }

    @Serializable
    data class DownloadRequest(val url: String)

    fun queueDownloadFor(searchResult: SearchResult) {
        screenModelScope.launch {
            globalHttpClient.post(Settings().get<String>("endpoint")!! + "/api/download") {
                buildHeaders {
                    contentType(ContentType.Application.Json)
                }
                this.setBody(DownloadRequest(searchResult.url))
            }
        }
    }

    fun nextPage() {
        mutableState.update {
            it.copy(offset = it.offset + it.results.size)
        }
        search(state.value.currentQuery, state.value.currentSearcher)
    }
}

class SearchScreen(val navigator: WindowCapableNavigator<Screen>) : Screen {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        var query by remember { mutableStateOf("") }
        val model = rememberScreenModel { SearchScreenModel() }
        val state by model.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val searchers = remember { mutableStateListOf<String>() }
        val searcher = state.currentSearcher
        LaunchedEffect(Unit) {
            val res = globalHttpClient.get(Settings().get<String>("endpoint")!! + "/api/searchers").body<List<String>>()
            searchers.clear()
            searchers.addAll(res)
            if (searcher == "") {
                model.setSearcher(searchers.first())
            }
        }

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().weight(1f)) {
                Row {
                    TextField(query, onValueChange = {
                        query = it
                    })
                    val focusManager = LocalFocusManager.current
                    Button(onClick = {
                        focusManager.clearFocus()
                        model.search(query, searcher)
                    }) {
                        Text("Search!")
                    }
                    if (state.mostRecentSearch.isNotBlank()) {
                        Button(onClick = {
                            focusManager.clearFocus()
                            model.search(state.mostRecentSearch, searcher)
                        }) {
                            Text(state.mostRecentSearch)
                        }
                    }
                    for (possibleSearcher in searchers) {
                        Button(enabled = possibleSearcher != searcher, onClick = {
                            model.setSearcher(possibleSearcher)
                        }) {
                            Text(possibleSearcher)
                        }
                    }
                    NextPageButton(model)
                }

                val lazyGridState = rememberLazyGridState()
                LaunchedEffect(Unit) {

                    snapshotFlow { state.results }.drop(1).collect {
                        println(it.take(1))
                        lazyGridState.scrollToItem(0)
                    }
                }
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxWidth(),
                    columns = GridCells.Fixed(3),
                    state = lazyGridState
                ) {
                    items(state.results) {
                        Column {
                            GenericImageCell(
                                it.thumbUrl,
                                it.title,
                                Modifier.combinedClickable(
                                    onClick = {
                                        navigator.goNewWindow(
                                            VideoPlayerScreen(
                                                model.loadVideoFor(it), navigator, cta = {
                                                    Button(onClick = {
                                                        model.queueDownloadFor(it)
                                                    }) {
                                                        Text("Download!")
                                                    }
                                                }
                                            )
                                        )
                                    },
                                    onLongClick = {
                                        navigator.goNewWindow(WebScreen(it.url))
                                    }
                                )
                            )
                            DownloadButton(it.url)
                        }
                    }
                    if (state.results.isNotEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Row(Modifier.fillMaxWidth()) {
                                NextPageButton(model)
                            }
                        }
                    }
                }
            }
            Button(modifier = Modifier.wrapContentSize(), onClick = { navigator.goBack() }) { Text("Back") }
        }
    }

    private @Composable
    fun DownloadButton(url: String) {
        Button(
            onClick = {
                // TODO: wire up download
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download")
        }
    }

    @Composable
    private fun NextPageButton(model: SearchScreenModel) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            model.nextPage()
        }) {
            Text("Next page!")
        }
    }
}