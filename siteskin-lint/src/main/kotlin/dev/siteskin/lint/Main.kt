package dev.siteskin.lint

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.origin.SiteOrigin
import java.io.PrintStream
import java.net.URI
import kotlin.system.exitProcess

/** Command-line entry point for validating a live SiteSkin origin. */
public fun main(args: Array<String>) {
    exitProcess(Command(ManifestDiscovery()).run(args, System.out, System.err))
}

internal class Command(private val loader: ManifestLoader) {
    fun run(args: Array<String>, output: PrintStream, error: PrintStream): Int {
        if (args.size != 1) return usage(error)
        val origin = parseHttpsOrigin(args.single()) ?: return usage(error)
        return when (val loaded = loader.load(origin)) {
            is ManifestLoadResult.Failed -> {
                error.println("siteskin-lint: ${loaded.message}")
                EXIT_FAILURE
            }
            is ManifestLoadResult.Validated -> render(loaded.outcome, output)
        }
    }

    private fun render(outcome: SiteSkinValidationOutcome, output: PrintStream): Int {
        val diagnostics = when (outcome) {
            is SiteSkinValidationOutcome.Accepted -> outcome.diagnostics
            is SiteSkinValidationOutcome.Rejected -> outcome.diagnostics
        }
        diagnostics.forEach { diagnostic ->
            output.println(listOfNotNull(diagnostic.code.value, diagnostic.pointer).joinToString(" "))
        }
        return if (outcome is SiteSkinValidationOutcome.Accepted) EXIT_SUCCESS else EXIT_FAILURE
    }

    private fun usage(error: PrintStream): Int {
        error.println("usage: siteskin-lint <https://origin>")
        return EXIT_USAGE
    }

    private fun parseHttpsOrigin(raw: String): SiteOrigin? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (hasNonOriginComponents(uri)) return null
        return SiteOrigin.parse(raw)
    }

    private fun hasNonOriginComponents(uri: URI): Boolean =
        uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || hasPath(uri)

    private fun hasPath(uri: URI): Boolean = uri.rawPath != "" && uri.rawPath != "/"

    private companion object {
        const val EXIT_SUCCESS = 0
        const val EXIT_FAILURE = 1
        const val EXIT_USAGE = 2
    }
}
