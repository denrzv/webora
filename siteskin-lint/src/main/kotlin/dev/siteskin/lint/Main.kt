package dev.siteskin.lint

import dev.siteskin.core.SiteSkinSchema

/**
 * `siteskin-lint https://site.example`
 *
 * Validates a live origin's manifest using the same code path the browser uses, so a passing lint
 * is a guarantee that SiteSkin mode will activate. Real validation lands in SPEC-003 once
 * CORE-002..005 exist; today this only reports what it would fetch.
 */
public fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: siteskin-lint <https://origin>")
        kotlin.system.exitProcess(2)
    }

    val origin = args[0].trimEnd('/')
    println("siteskin-lint — schema ${SiteSkinSchema.CURRENT}")
    println("manifest URL: $origin${SiteSkinSchema.WELL_KNOWN_PATH}")
    println()
    println("Validation not yet implemented — see SPEC-003.")
    kotlin.system.exitProcess(0)
}
