package app.webora.browser.visual

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * The hosted Pixel run serves two different purposes and keeps them separate on purpose:
 * `LiveSiteScreenshotTest` publishes the human-facing canonical showcase, while
 * `LiveSiteNavigationSmokeTest` exercises deeper product/history/mode transitions without adding
 * diagnostic frames to the visual artifact.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    LiveSiteScreenshotTest::class,
    LiveSiteNavigationSmokeTest::class,
)
class LiveSiteHostedSuite
