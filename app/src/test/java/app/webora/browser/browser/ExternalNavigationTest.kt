package app.webora.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalNavigationTest {
    @Test
    fun `supported schemes become closed inert requests`() {
        assertEquals(ExternalNavigation.Kind.EMAIL, externalNavigation("mailto:hello@example.com")?.kind)
        assertEquals(ExternalNavigation.Kind.TELEPHONE, externalNavigation("tel:+12025550123")?.kind)
        assertEquals(ExternalNavigation.Kind.MAP, externalNavigation("geo:0,0?q=coffee")?.kind)
    }

    @Test
    fun `arbitrary and malformed schemes fail closed`() {
        assertNull(externalNavigation("intent://attack/#Intent;component=evil;end"))
        assertNull(externalNavigation("javascript:alert(1)"))
        assertNull(externalNavigation("mailto:"))
        assertNull(externalNavigation("not a uri"))
    }
}
