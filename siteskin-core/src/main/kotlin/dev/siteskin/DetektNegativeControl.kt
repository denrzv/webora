package dev.siteskin

internal fun detektNegativeControl(value: String): Int {
    var score = 0
    if ("a" in value) score++
    if ("b" in value) score++
    if ("c" in value) score++
    if ("d" in value) score++
    if ("e" in value) score++
    if ("f" in value) score++
    if ("g" in value) score++
    if ("h" in value) score++
    if ("i" in value) score++
    if ("j" in value) score++
    if ("k" in value) score++
    return score
}
