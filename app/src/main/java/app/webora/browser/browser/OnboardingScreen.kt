package app.webora.browser.browser

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.webora.browser.R

private val onboardingPages = listOf(
    OnboardingPage(R.string.onboarding_browsing_title, R.string.onboarding_browsing_body),
    OnboardingPage(R.string.onboarding_adaptation_title, R.string.onboarding_adaptation_body),
    OnboardingPage(R.string.onboarding_control_title, R.string.onboarding_control_body),
)

private data class OnboardingPage(@param:StringRes val title: Int, @param:StringRes val description: Int)

@Composable
internal fun OnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    val lastPage = pageIndex == onboardingPages.lastIndex
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(40.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.onboarding_step, pageIndex + 1, onboardingPages.size))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WeboraTextButton(onClick = onComplete) { Text(stringResource(R.string.onboarding_skip)) }
            WeboraButton(onClick = { if (lastPage) onComplete() else pageIndex += 1 }) {
                Text(stringResource(if (lastPage) R.string.onboarding_start else R.string.onboarding_next))
            }
        }
    }
}
