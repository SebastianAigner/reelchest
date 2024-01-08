import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
        val mostRecentSearch: String
    )

    fun search(query: String) {
        Settings().putString("recentsearch", query)
        mutableState.update { it.copy(currentQuery = query, mostRecentSearch = query) }
        screenModelScope.launch {
            val res =
                globalHttpClient.post(Settings().get<String>("endpoint")!! + "/api/search/http://localhost:9091/search") {
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
        search(state.value.currentQuery)
    }
}

class SearchScreen(val navigator: WindowCapableNavigator<Screen>) : Screen {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        var query by remember { mutableStateOf("") }
        val model = rememberScreenModel { SearchScreenModel() }
        val state by model.state.collectAsState()

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().weight(1f)) {
                Row {
                    TextField(query, onValueChange = {
                        query = it
                    })
                    val focusManager = LocalFocusManager.current
                    Button(onClick = {
                        focusManager.clearFocus()
                        model.search(query)
                    }) {
                        Text("Search!")
                    }
                    if (state.mostRecentSearch.isNotBlank()) {
                        Button(onClick = {
                            focusManager.clearFocus()
                            model.search(state.mostRecentSearch)
                        }) {
                            Text(state.mostRecentSearch)
                        }
                    }
                    NextPageButton(model)
                }

                val lazyGridState = rememberLazyGridState()
                //            LaunchedEffect(state.results) {
                //                println("I'm scrolling up! ${state.results.take(2)}")
                //                lazyGridState.scrollToItem(0)
                //            }
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
                        GenericImageCell(
                            it.thumbUrl,
                            it.title,
                            Modifier.combinedClickable(
                                onClick = {

                                    navigator.goNewWindow(
                                        VideoScreen(
                                            model.loadVideoFor(it), navigator
                                        ) {
                                            Button(onClick = {
                                                model.queueDownloadFor(it)
                                            }) {
                                                Text("Download!")
                                            }
                                        }
                                    )
                                },
                                onLongClick = {
                                    navigator.goNewWindow(WebScreen(it.url))
                                }
                            )
                        )
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

    @Composable
    private fun NextPageButton(model: SearchScreenModel) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            model.nextPage()
        }) {
            Text("Next page!")
        }
    }
}