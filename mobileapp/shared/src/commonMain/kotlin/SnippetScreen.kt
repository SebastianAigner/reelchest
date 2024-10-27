import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.Settings
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class SnippetScreen(
    private val id: String,
    private val beginning: Double,
    private val end: Double,
    val navigator: WindowCapableNavigator<Screen>,
) : Screen {

    @Composable
    override fun Content() {
        BasicComposable(beginning, end)
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    @Composable
    fun BasicComposable(beginning: Double, end: Double) {
        val tags = remember { mutableStateOf("") }
        val popularTags = remember { mutableStateListOf<PopularTag>() }
        var status by remember { mutableStateOf("") }
        LaunchedEffect(beginning, end) {
            val new = globalHttpClient.get("${Settings().getStringOrNull("snippetEndpoint")!!}/tags/popular")
                .body<List<PopularTag>>()
            popularTags.clear()
            popularTags.addAll(new)
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = {
                navigator.goBack()
            }) {
                Text("Back")
            }
            Text(text = "Beginning: $beginning")
            Text(text = "End: $end")
            OutlinedTextField(
                value = tags.value,
                onValueChange = { tags.value = it },
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            val cs = rememberCoroutineScope()
            Button(onClick = {
                cs.launch {
                    status = save(id, beginning, end, tags.value)
                }
            }) {
                Text("Save")
            }
            Text(status)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (popularTag in popularTags) {
                        Button(onClick = {
                            if (!tags.value.endsWith(",")) {
                                tags.value += ","
                            }
                            tags.value += popularTag.tags + ","
                        }) {
                            Text("${popularTag.tags} (x${popularTag.popularity})")
                        }
                    }
                }

                val otherSnippets = remember { mutableStateListOf<TimeSnippet>() }
                LaunchedEffect(beginning, end, status) {
                    val snippets =
                        globalHttpClient.get("${Settings().getStringOrNull("snippetEndpoint")!!}/timesnippets/belongsTo/$id")
                    if (snippets.status != HttpStatusCode.OK) {
                        return@LaunchedEffect
                    }
                    otherSnippets.clear()
                    otherSnippets.addAll(snippets.body())
                }
                Text("Other snippets")
                for (snippet in otherSnippets) {
                    Text(
                        "[${snippet.tags.joinToString()}]-${snippet.beginning} to ${snippet.end}",
                        modifier = Modifier.combinedClickable(onLongClick = {
                            cs.launch {
                                status =
                                    globalHttpClient
                                        .delete("${Settings().getStringOrNull("snippetEndpoint")!!}/timesnippets/${snippet.id!!}")
                                        .body<String>()
                            }
                        }, onClick = {})
                    )
                }
            }
        }
    }

    suspend fun save(id: String, beginning: Double, end: Double, tagsString: String): String {
        val ts = TimeSnippet(id, beginning, end, tagsString.split(",").map { it.trim() }.filter { it.isNotBlank() })
        val res = globalHttpClient.post("${Settings().getStringOrNull("snippetEndpoint")!!}/timesnippets") {
            contentType(ContentType.Application.Json)
            setBody(ts)
        }.body<String>()
        println(res)
        return res
    }
}

@Serializable
data class TimeSnippet(
    var belongsTo: String,
    var beginning: Double = 0.0,
    var end: Double = 0.0,
    var tags: List<String>,
    val id: String? = null,
)

@Serializable
data class PopularTag(
    val popularity: Int,
    val tags: String,
)