package app.webora.browser.design

import androidx.compose.ui.graphics.Color

/**
 * A colour-carrying value's roles by name, read from the class rather than from a list.
 *
 * The reason the tests reflect instead of enumerating: a hand-written list of roles is correct on
 * the day it is written and silently stops covering the next one added. Reflection makes the
 * completeness assertions true for tokens that do not exist yet, which is the same reason
 * `BrowserSurfaceConventionsTest` discovers its scanned set instead of listing it.
 *
 * `Color` is a value class over `ULong`, so Kotlin compiles each accessor to a name-mangled getter
 * returning a primitive `long` — `getGround-0d7_KjU()J`. Java reflection finds them without
 * `kotlin-reflect`, which is not on the test classpath and would be a dependency added to satisfy a
 * test rather than a product need. The `long` bits are the `ULong` bits, so the value round-trips
 * exactly.
 *
 * Used for both `WeboraColorScheme` and Material's `ColorScheme`, which is what lets the closure
 * assertion compare one against the other by value.
 */
internal fun Any.colorRoles(): Map<String, Color> = javaClass.methods
    .filter { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
    .filter { it.name.startsWith(GETTER_PREFIX) }
    .associate { method ->
        val name = method.name
            .removePrefix(GETTER_PREFIX)
            .substringBefore(MANGLE_SEPARATOR)
            .replaceFirstChar(Char::lowercaseChar)
        name to Color((method.invoke(this) as Long).toULong())
    }

private const val GETTER_PREFIX = "get"
private const val MANGLE_SEPARATOR = '-'
