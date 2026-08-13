package app.webora.browser.browser

import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import app.webora.browser.R
import app.webora.browser.design.WeboraSpacing

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
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(WeboraSpacing.GUTTER),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(WeboraSpacing.LARGE))
        OnboardingPageCard(page = page, pageIndex = pageIndex)
        Spacer(Modifier.height(WeboraSpacing.LARGE))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WeboraTextButton(onClick = onComplete) { Text(stringResource(R.string.onboarding_skip)) }
            WeboraButton(onClick = { if (lastPage) onComplete() else pageIndex += 1 }) {
                Text(stringResource(if (lastPage) R.string.onboarding_start else R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun OnboardingPageCard(page: OnboardingPage, pageIndex: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(WeboraSpacing.GUTTER),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
        ) {
            Text(
                stringResource(R.string.onboarding_step, pageIndex + 1, onboardingPages.size),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(page.title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(page.description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
