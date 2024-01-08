import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class MetadatedDownloadQueueEntry(val queueEntry: DownloadTaskDTO, val title: String)

@Serializable
data class DownloadTaskDTO(val originUrl: String, val progress: Double)


@Serializable
data class ProblematicTaskDTO(
    val originUrl: String,
    val error: String
)

class DownloadScreenModel() : StateScreenModel<DownloadScreenModel.DownloadScreenState>(
    DownloadScreenState(listOf(), listOf())
) {

    data class DownloadScreenState(
        val queued: List<MetadatedDownloadQueueEntry>,
        val problematic: List<ProblematicTaskDTO>
    )

    fun updateQueued() {
        screenModelScope.launch {
            val res = globalHttpClient.get(Settings().get<String>("endpoint")!! + "/api/queue")
                .body<List<MetadatedDownloadQueueEntry>>()
            mutableState.update {
                it.copy(queued = res)
            }
        }
    }

    fun updateAll() {
        updateProblematic()
        updateQueued()
    }

    fun updateProblematic() {
        screenModelScope.launch {
            val endpoint = Settings().getStringOrNull("endpoint") ?: return@launch
            val res = globalHttpClient
                .get("$endpoint/api/problematic")
                .body<List<ProblematicTaskDTO>>()
            mutableState.update {
                it.copy(problematic = res)
            }
        }
    }
}

class DownloadsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DownloadScreenModel() }
        val state by screenModel.state.collectAsState()
        LaunchedEffect(Unit) {
            screenModel.updateAll()
        }
        Box(Modifier.fillMaxSize()) {
            Column {
                Text("Q U E U E D")
                for (queued in state.queued) {
                    val perc = (queued.queueEntry.progress * 100).toInt()
                    val nam = queued.title
                    val originUrl = queued.queueEntry.originUrl
                    Text("[$perc%] $originUrl ($nam)")
                }
                Text("P R O B L E M A T I C")
                for (prob in state.problematic) {
                    val url = prob.originUrl
                    val err = prob.error
                    Text("$url: $err")
                }
                Button(onClick = { navigator.pop() }) {
                    Text("Back")
                }
                Button(onClick = { screenModel.updateAll() }) {
                    Text("Refresh")
                }
            }
        }
    }
}