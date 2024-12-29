import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.delay

object SetupScreen : Screen {
    val settings = Settings()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var configuration by remember {
            mutableStateOf(
                settings.get<String>("allEndpoints")?.split(",")?.firstOrNull()
            )
        }
        var showInstances by remember { mutableStateOf(false) }
        var isValid by remember { mutableStateOf(false) }
        var statusFieldText by remember { mutableStateOf("") }
        LaunchedEffect(isValid) {
            if (isValid) {
                statusFieldText = "Valid connection! Connecting..."
                delay(2000)
                val currentlyConfigured = settings.get<String>("endpoint")
                if (currentlyConfigured != configuration) {
                    // we clear to prepare for connection to a new instance
                    CacheInvalidator().invalidate()
                }
                settings.putString("endpoint", configuration!!)
                navigator.push(VideoListScreen(navigator.toMyNavigator()))
            }
        }
        LaunchedEffect(configuration) {
            val currentConfiguration = configuration ?: return@LaunchedEffect
            try {
                statusFieldText = "Checking connectivity..."
                val canAccessLogsEndpoint = globalHttpClient.get("$currentConfiguration/api/log")
                    .status
                isValid = canAccessLogsEndpoint == HttpStatusCode.OK
            } catch (e: Exception) {
                isValid = false
                statusFieldText = e.stackTraceToString()
                println("Oh no! $e")
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).padding(20.dp), contentAlignment = Alignment.Center) {
            Box() {
                Box(
                    Modifier
                        .scale(1.02f)
                        .blur(
                            30.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        ) // neon glow effect! (wish there was a better way to do this)
                        .matchParentSize()
                        .background(Color.Green)
                        .padding(10.dp)
                        .scale(1.3f)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(20.dp)
                        )
                        .background(Color.White)
                        .padding(20.dp)

                ) {
                    Text(" リール\nチェスト", style = MaterialTheme.typography.h3)
                    Text(
                        "(Reelchest)",
                        fontSize = MaterialTheme.typography.h5.fontSize,
                        lineHeight = MaterialTheme.typography.h5.lineHeight
                    )
                    Text("Enter the URL of your Reelchest server")
                    TextField(configuration ?: "", onValueChange = {
                        configuration = it
                    })
                    Text(statusFieldText, modifier = Modifier.clickable {
                        showInstances = !showInstances
                        isValid = false
                        statusFieldText = "Waiting for instance."
                    })
                    Row {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "PlayArrow", modifier = Modifier.clickable {
                            navigator.push(TikTokScreen())
                        })
                        Icon(Icons.Filled.Home, contentDescription = "PlayArrow", modifier = Modifier.clickable {
                            navigator.push(WindowManaScreen())
                        })
                    }
                    if (showInstances) {
                        var newEndpointText by remember { mutableStateOf("") }
                        Column {
                            val allEndpoints = Settings().get<String>("allEndpoints")
                            Text(allEndpoints ?: "")
                            allEndpoints?.split(",")?.forEach {
                                Row {
                                    Text(it, modifier = Modifier.clickable {
                                        configuration = it
                                    })
                                    Button(onClick = {
                                        Settings().set(
                                            "allEndpoints",
                                            (Settings().get<String>("allEndpoints") ?: "")
                                                .replace(it, "")
                                                .replace(",,", "")
                                        )
                                    }) {
                                        Text("Remove")
                                    }
                                }
                            }
                            Row {
                                TextField(newEndpointText, onValueChange = {
                                    newEndpointText = it
                                })
                                Button(onClick = {
                                    Settings().set(
                                        "allEndpoints",
                                        Settings().get<String>("allEndpoints") + "," + newEndpointText
                                    )
                                }) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class CacheInvalidator {
    val config = RealmConfiguration.create(schema = setOf(MediaLibraryRealmEntry::class))
    val realm: Realm = Realm.open(config)
    fun invalidate() {
        // todo: this is kind of a hack, would love to do this cleaner
        // todo: one cache per instance rather than a global-to-be-deleted cache
        realm.writeBlocking {
            this.deleteAll()
        }
    }
}