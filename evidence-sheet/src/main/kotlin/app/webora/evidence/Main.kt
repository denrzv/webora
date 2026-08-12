package app.webora.evidence

import java.io.PrintStream
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "usage: evidence-sheet <screenshot-directory>"

/**
 * Composes `preview.png` from the canonical frames in one directory.
 *
 * Prints `tiles=N` on success and nothing else. The screenshot workflow compares that number against
 * the count of screenshots the run collected and fails when they disagree, which is what stops a
 * composer bug from publishing a sheet that is quietly short a frame. Because the line is a contract
 * rather than a log message, a failing run prints **no** `tiles=` line at all: an absent count and a
 * wrong count must not look alike to the shell reading it.
 */
fun main(args: Array<String>) {
    exitProcess(runContactSheetCommand(args.toList(), System.out, System.err))
}

/**
 * The command with its streams and exit code as values, so the contract above is testable without
 * spawning a JVM or capturing global state. [main] is the only place a process actually exits.
 */
internal fun runContactSheetCommand(args: List<String>, out: PrintStream, err: PrintStream): Int {
    val directory = args.singleOrNull()
    if (directory == null) {
        err.println(USAGE)
        return 2
    }

    return try {
        out.println("tiles=" + composeContactSheet(Path.of(directory)))
        0
    } catch (failure: ContactSheetFailure) {
        err.println(failure.message)
        1
    }
}
