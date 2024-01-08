import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class FeedEntry(
    val imageUrl: String,
    val originUrl: String,
    val uuid: String
)

data class QueueScreenState(
    val currentFocused: Int,
    val queue: List<FeedEntry>
)

class QueueScreenModel() : StateScreenModel<QueueScreenState>(
    QueueScreenState(0, emptyList())
) {

    private suspend fun postToEndpoint(endpoint: String, qe: FeedEntry) {
        globalHttpClient.post(Settings().get<String>("endpoint")!! + "/feed/$endpoint") {
            contentType(ContentType.Text.Plain)
            setBody(qe.uuid)
        }
    }

    fun accept(qe: FeedEntry) {
        screenModelScope.launch {
            postToEndpoint("accept", qe)
            updateQueue()
        }
    }

    fun decline(qe: FeedEntry) {
        screenModelScope.launch {
            postToEndpoint("decline", qe)
            updateQueue()
        }
    }

    fun skip(qe: FeedEntry) {
        screenModelScope.launch {
            postToEndpoint("skip", qe)
            updateQueue()
        }
    }

    fun updateQueue() {
        screenModelScope.launch {
            val entries = globalHttpClient.get(Settings().get<String>("endpoint")!! + "/feed")
                .body<List<FeedEntry>>()
            mutableState.update {
                it.copy(queue = entries)
            }
        }
    }
}

class QueueScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { QueueScreenModel() }
        val state by screenModel.state.collectAsState()
        LaunchedEffect(Unit) {
            screenModel.updateQueue()
        }
        Column(Modifier.fillMaxWidth()) {
            state.queue.firstOrNull()?.let {
                DecisionCard(
                    it,
                    onAccept = {
                        screenModel.accept(it)
                    },
                    onDecline = {
                        screenModel.decline(it)
                    },
                    onSkip = {
                        screenModel.skip(it)
                    }
                )
            }
            Row(Modifier.fillMaxWidth()) {
                state.queue.drop(1).forEach {
                    DecisionCard(it, {}, {}, {})
                }
            }
        }
    }

    private @Composable
    fun DecisionCard(
        it: FeedEntry,
        onAccept: () -> Unit,
        onDecline: () -> Unit,
        onSkip: () -> Unit
    ) {
        Column(Modifier.fillMaxSize()) {
            KamelImage(
                resource = asyncPainterResource(it.imageUrl),
                contentDescription = null,
                modifier = Modifier.height(200.dp)
            )
            Row {
                Button(onAccept) {
                    Text("Accept")
                }
                Button(onSkip) {
                    Text("Skip")
                }
                Button(onDecline) {
                    Text("Decline")
                }
                val navigator = LocalNavigator.currentOrThrow
                Button({
                    navigator.pop()
                }) {
                    Text("Back")
                }
            }
        }

    }
}