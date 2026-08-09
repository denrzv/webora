package app.webora.browser.browser

internal enum class LaunchDestination {
    Onboarding,
    Home,
}

internal fun launchDestination(onboardingCompleted: Boolean): LaunchDestination =
    if (onboardingCompleted) LaunchDestination.Home else LaunchDestination.Onboarding
