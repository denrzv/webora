package dev.siteskin.core

import dev.siteskin.core.action.ResolvedAction
import dev.siteskin.core.model.SiteSkinConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class TrustedModelApiTest {
    @Test fun `trusted configuration has no public constructor or copy escape hatch`() {
        val hasPublicConstructor = SiteSkinConfiguration::class.java.constructors
            .any { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        assertFalse(hasPublicConstructor)
        assertFalse(SiteSkinConfiguration::class.java.methods.any { it.name == "copy" })
    }

    @Test fun `resolved actions form a closed semantic capability set`() {
        assertTrue(ResolvedAction::class.java.isSealed)
        assertEquals(
            setOf(
                "ComposeEmail", "Dial", "NavigateExternal", "NavigateInternal",
                "OpenMap", "OpenMenu", "Refresh", "Share",
            ),
            ResolvedAction::class.java.permittedSubclasses.map { it.simpleName }.toSet(),
        )
    }
}
