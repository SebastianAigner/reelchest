package verbiage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.dp
import io.sebi.webview.BrowserView

@Composable
fun Verbiage() {
    val tenses = listOf("present", "past", "future")
    val verbs = listOf("essen", "trinken", "schmecken")
    var currentTense by remember { mutableStateOf(tenses.random()) }
    var currentVerb by remember { mutableStateOf(verbs.random()) }
    var currentGuess by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Column(Modifier.fillMaxSize().padding(7.dp)) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.5f),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("The verb is", style = MaterialTheme.typography.h4)
                Text(currentVerb, style = MaterialTheme.typography.h2, fontWeight = Bold)
                Spacer(Modifier.height(15.dp))
                Text("The tense is", style = MaterialTheme.typography.h4)
                Text(currentTense, style = MaterialTheme.typography.h2, fontWeight = Bold)
                Spacer(Modifier.height(15.dp))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.h3,
                    value = currentGuess,
                    singleLine = true,
                    onValueChange = { currentGuess = it }
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        url = makeUrl(currentVerb, currentTense, currentGuess)
                        focusManager.clearFocus()
                    }
                ) { Text("Check") }
            }
        }
        AnimatedVisibility(url.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(1f)) {
                BrowserView(url, Modifier.fillMaxSize())
                Box(
                    Modifier.size(60.dp).background(Color.White)
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .clickable {
                            url = ""
                            currentTense = (tenses - currentTense).random()
                            currentVerb = (verbs - currentVerb).random()
                            currentGuess = ""
                        }
                ) {
                    Icon(Icons.Filled.CheckCircle, "Dismiss", Modifier.fillMaxSize(), tint = Color.Gray)
                }
            }
        }
    }
}

fun makeUrl(verb: String, tense: String, guess: String): String {
    return "https://chatgpt.com/?q=${makeQuestion(verb, tense, guess)}"
}

fun makeQuestion(verb: String, tense: String, guess: String): String {
    return "I have conjugated the verb $verb in the tense $tense. Is '$guess' the correct answer?"
}