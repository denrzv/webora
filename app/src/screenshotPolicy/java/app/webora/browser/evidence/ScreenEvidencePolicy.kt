package app.webora.browser.evidence

sealed interface FocusVerdict {
    data class OwnedByApp(val title: String) : FocusVerdict
    data class DismissableSystemAnr(val processName: String, val title: String) : FocusVerdict
    data class Blocked(val reason: String) : FocusVerdict
}

/**
 * Fail-closed ownership policy for hosted screenshot evidence.
 *
 * A focused title that names Webora directly is accepted only after system error-dialog shapes have
 * been ruled out. Android/Compose may also focus an application sub-window whose title is generic
 * (for example `Pop-Up Window`). Such a window is accepted only when the same OS dump proves all of
 * the following: the focused app is Webora, the focused window block belongs to Webora, its type is
 * `APPLICATION_SUB_PANEL`, and its parent window belongs to Webora. A generic title by itself is
 * never sufficient.
 */
object ScreenEvidencePolicy {
    const val DISMISSABLE_ANR_PROCESS = "com.android.systemui"

    private const val ANR_TITLE_PREFIX = "Application Not Responding: "
    private const val CRASH_TITLE_PREFIX = "Application Error: "
    private const val APPLICATION_SUB_PANEL = "APPLICATION_SUB_PANEL"

    private val CURRENT_FOCUS =
        Regex("""mCurrentFocus=(?:(null)|Window\{(\S+) \S+ ([^}]*)\})""")
    private val WINDOW_HEADER = Regex("""^\s*Window #\d+ Window\{(\S+)\s+.*:\s*$""")

    fun focusVerdict(dumpsysWindowOutput: String, appPackage: String): FocusVerdict {
        val matches = CURRENT_FOCUS.findAll(dumpsysWindowOutput).toList()
        if (matches.isEmpty()) {
            return FocusVerdict.Blocked("no mCurrentFocus line in the dumpsys window output")
        }

        val focuses = matches.map { match ->
            if (match.groupValues[1].isNotEmpty()) {
                FocusedWindow(id = null, title = "null")
            } else {
                FocusedWindow(
                    id = match.groupValues[2],
                    title = match.groupValues[3].trim(),
                )
            }
        }.distinct()

        if (focuses.size > 1) {
            return FocusVerdict.Blocked(
                "two focused windows disagree: ${focuses.joinToString(" | ") { it.title }}",
            )
        }

        val focus = focuses.single()
        return classify(focus, dumpsysWindowOutput, appPackage)
    }

    private fun classify(
        focus: FocusedWindow,
        dump: String,
        appPackage: String,
    ): FocusVerdict {
        val title = focus.title
        return when {
            title == "null" -> FocusVerdict.Blocked("mCurrentFocus=null; nothing owns the display")
            title.isEmpty() -> FocusVerdict.Blocked("the focused window has no title")
            title.startsWith(ANR_TITLE_PREFIX) -> anrVerdict(title)
            title.startsWith(CRASH_TITLE_PREFIX) ->
                FocusVerdict.Blocked("a process has crashed and its dialog owns the screen: $title")
            containsPackageToken(title, appPackage) -> FocusVerdict.OwnedByApp(title)
            focus.id != null && isProvablyOwnedApplicationSubWindow(focus.id, dump, appPackage) ->
                FocusVerdict.OwnedByApp(title)
            else -> FocusVerdict.Blocked("an unrecognised window owns the screen: $title")
        }
    }

    private fun isProvablyOwnedApplicationSubWindow(
        focusedWindowId: String,
        dump: String,
        appPackage: String,
    ): Boolean {
        val focusedAppOwned = dump.lineSequence()
            .filter { it.contains("mFocusedApp=") }
            .any { containsPackageToken(it, appPackage) }
        if (!focusedAppOwned) return false

        val block = focusedWindowBlock(dump, focusedWindowId) ?: return false
        val packageOwned = block.lineSequence().any { line ->
            Regex("""\bpackage=${Regex.escape(appPackage)}(?:\s|$)""").containsMatchIn(line)
        }
        val applicationSubPanel = block.lineSequence().any { line ->
            line.contains("type=$APPLICATION_SUB_PANEL") ||
                line.contains("ty=$APPLICATION_SUB_PANEL") ||
                line.contains("type=1002") ||
                line.contains("ty=1002")
        }
        val parentOwned = block.lineSequence()
            .filter { it.contains("mParentWindow=") }
            .any { containsPackageToken(it, appPackage) }

        return packageOwned && applicationSubPanel && parentOwned
    }

    private fun focusedWindowBlock(dump: String, focusedWindowId: String): String? {
        val lines = dump.lines()
        val start = lines.indexOfFirst { line ->
            val match = WINDOW_HEADER.matchEntire(line)
            match != null && match.groupValues[1] == focusedWindowId
        }
        if (start < 0) return null

        val end = (start + 1 until lines.size)
            .firstOrNull { index -> WINDOW_HEADER.matches(lines[index]) }
            ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    private fun containsPackageToken(text: String, appPackage: String): Boolean {
        val boundary = "[A-Za-z0-9._]"
        return Regex("(?<!$boundary)${Regex.escape(appPackage)}(?!$boundary)").containsMatchIn(text)
    }

    private fun anrVerdict(title: String): FocusVerdict {
        val processName = title.removePrefix(ANR_TITLE_PREFIX).trim()
        return if (processName == DISMISSABLE_ANR_PROCESS) {
            FocusVerdict.DismissableSystemAnr(processName, title)
        } else {
            FocusVerdict.Blocked("$processName is not responding, and only System UI may be waited on")
        }
    }

    private data class FocusedWindow(
        val id: String?,
        val title: String,
    )
}
