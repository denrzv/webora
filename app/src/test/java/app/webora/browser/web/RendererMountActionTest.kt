package app.webora.browser.web

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mount decision, one case per situation a retained renderer can be mounted in.
 *
 * `BROWSE-006` made renderers outlive their Compose hosts so live back/forward history survives a
 * tab switch, and `HardenedWebView` kept asking `existing == null` — "is this `WebView` new?" — as
 * its proxy for "does this renderer need the page?". A tab that returned to Home and navigated again
 * therefore never loaded. This is the decision that replaces it, extracted from the `@Composable`
 * because the gate cannot drive Compose and a rule inside `factory` would be verified by nothing.
 */
class RendererMountActionTest {

    @Test fun `a fresh renderer loads`() {
        // The behaviour `existing == null` used to provide, which must survive its removal.
        val action = rendererMountAction(hosted = null, target = PAGE, isLoading = true)

        assertEquals(RendererMountAction.Load(PAGE), action)
    }

    @Test fun `a home round trip to a new address loads`() {
        // The reported defect: page -> Home -> type any address. The renderer is retained and holds
        // the previous page; the tab's committed target is the new one.
        assertEquals(
            RendererMountAction.Load(OTHER),
            rendererMountAction(hosted = PAGE, target = OTHER, isLoading = true),
        )
    }

    @Test fun `a tab switch back to the same page is silent`() {
        // BROWSE-009 acceptance criterion 2: switching back reattaches the same instance without a
        // reload. This is the row a fix built on WebView.getUrl() gets wrong.
        assertEquals(RendererMountAction.Ready, rendererMountAction(hosted = PAGE, target = PAGE, isLoading = false))
    }

    @Test fun `a page reached by an in-page link is silent on switch`() {
        // A link click never passes through controller.navigate, so `hosted` is maintained from the
        // renderer's own reports as well as from browser requests. Without that half this row loads.
        val controller = BrowserWebViewController(tabId = 1L)
        controller.navigate(PAGE)
        controller.observed(LINKED)

        assertEquals(
            RendererMountAction.Ready,
            rendererMountAction(hosted = controller.hostedUrl, target = LINKED, isLoading = false),
        )
    }

    @Test fun `a failed tab is silent on switch`() {
        // A failed navigation must not move `hosted`: the browser's last request stands, so the
        // error tab does not re-issue the failing load every time it is selected.
        val controller = BrowserWebViewController(tabId = 1L)
        controller.navigate(UNREACHABLE)

        assertEquals(
            RendererMountAction.Ready,
            rendererMountAction(hosted = controller.hostedUrl, target = UNREACHABLE, isLoading = false),
        )
    }

    @Test fun `a waiting tab loads even when the renderer already holds the page`() {
        // Home -> the same address again. Nothing differs, but navigateFromHome set isLoading and no
        // callback is coming to clear it, so the tab is waiting for a page that will never arrive.
        // The first version of this reported a synthetic completion instead; a tab switched away
        // from mid-load has the same shape and would have been told its loading page was finished.
        assertEquals(
            RendererMountAction.Load(PAGE),
            rendererMountAction(hosted = PAGE, target = PAGE, isLoading = true),
        )
    }

    @Test fun `evaluating again after a load is silent`() {
        // The oscillation guard. Loading records the target, and the resulting observations report
        // the same URL, so a second evaluation cannot re-fire. Without this the fix could loop.
        val controller = BrowserWebViewController(tabId = 1L)
        controller.navigate(OTHER)
        controller.observed(OTHER)

        assertEquals(
            RendererMountAction.Ready,
            rendererMountAction(hosted = controller.hostedUrl, target = OTHER, isLoading = false),
        )
    }

    @Test fun `a tab with no committed url requests nothing`() {
        assertEquals(RendererMountAction.Ready, rendererMountAction(hosted = null, target = "", isLoading = false))
    }

    @Test fun `a destroyed renderer forgets its page`() {
        // The record lives on the controller, so a closed tab takes it with the renderer. Nothing
        // separate has to remember to forget it.
        val controller = BrowserWebViewController(tabId = 1L)
        controller.navigate(PAGE)
        controller.destroy()

        assertEquals(null, controller.hostedUrl)
    }

    private companion object {
        const val PAGE = "https://example.com/"
        const val OTHER = "https://other.example/"
        const val LINKED = "https://example.com/deep/page"
        const val UNREACHABLE = "http://127.0.0.1:1/"
    }
}
