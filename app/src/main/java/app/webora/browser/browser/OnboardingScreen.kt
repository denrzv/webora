package app.webora.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val onboardingPages = listOf(
    OnboardingPage("Browse the whole web", "Search or enter any web address. Webora is a browser first."),
    OnboardingPage("Sites can fit you better", "SiteSkin lets supported sites offer native-style navigation."),
    OnboardingPage(
        "You stay in control",
        "Webora keeps security details visible and asks before a site changes the browser.",
    ),
)

private data class OnboardingPage(val title: String, val description: String)

@Composable
internal fun OnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Webora", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(40.dp))
        Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(page.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text("Step ${pageIndex + 1} of ${onboardingPages.size}")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onComplete) { Text("Skip") }
            Button(
                onClick = {
                    if (pageIndex == onboardingPages.lastIndex) onComplete() else pageIndex += 1
                },
            ) { Text(if (pageIndex == onboardingPages.lastIndex) "Start browsing" else "Next") }
        }
    }
}
