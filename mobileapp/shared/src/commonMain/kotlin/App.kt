import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.Configuration
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import org.jetbrains.compose.resources.ExperimentalResourceApi


val globalHttpClient = HttpClient() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
        json {
            ignoreUnknownKeys = true
        }
    }
}

fun Configuration.json(
    contentType: ContentType = ContentType.Application.Json,
    jsonBuilder: JsonBuilder.() -> Unit
) {
    val json = Json { this.jsonBuilder() }
    serialization(contentType, json)
}


@OptIn(ExperimentalResourceApi::class, ExperimentalAnimationApi::class)
@Composable
fun App() {
    MaterialTheme {
        Navigator(SetupScreen)
    }
}


expect fun getPlatformName(): String