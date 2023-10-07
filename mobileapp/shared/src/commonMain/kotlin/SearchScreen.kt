import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
        emptyList()
    )
) {

    data class SearchScreenState(
        val results: List<SearchResult>,
        val offset: Int = 0,
        val currentQuery: String = ""
    )

    fun search(query: String) {
        mutableState.update { it.copy(currentQuery = query) }
        coroutineScope.launch {
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
        return Settings().get<String>("endpoint")!! + "decrypt?url=${searchResult.url}"
    }

    @Serializable
    data class DownloadRequest(val url: String)

    fun queueDownloadFor(searchResult: SearchResult) {
        coroutineScope.launch {
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

class SearchScreen : Screen {
    @Composable
    override fun Content() {
        var query by remember { mutableStateOf("") }
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SearchScreenModel() }
        val state by model.state.collectAsState()

        Column(Modifier.fillMaxSize()) {
            Row {
                TextField(query, onValueChange = {
                    query = it
                })
                Button(onClick = {
                    model.search(query)
                }) {
                    Text("Search!")
                }
                NextPageButton(model)
            }

            val lazyGridState = rememberLazyGridState()
            LaunchedEffect(state.results) {
                lazyGridState.scrollToItem(0)
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
                        onClick = {
                            navigator.push(
                                VideoScreen(
                                    model.loadVideoFor(it)
                                ) {
                                    Button(onClick = {
                                        model.queueDownloadFor(it)
                                    }) {
                                        Text("Download!")
                                    }
                                }
                            )
                        }
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
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                Button(onClick = {
                    navigator.pop()
                }) {
                    Text("Back")
                }
            }
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