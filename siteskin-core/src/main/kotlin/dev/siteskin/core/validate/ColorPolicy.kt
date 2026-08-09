package dev.siteskin.core.validate

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal data class CorrectedColor(val color: String, val corrected: Boolean)

internal object ColorPolicy {
    fun canonicalize(raw: String): String {
        val digits = raw.removePrefix("#")
        val expanded = if (digits.length == 3) digits.flatMap { listOf(it, it) }.joinToString("") else digits
        return "#${expanded.uppercase()}"
    }

    fun correct(background: String, text: String, target: Double): CorrectedColor {
        var channels = channels(canonicalize(background))
        val canonicalText = canonicalize(text)
        if (contrastRatio(format(channels), canonicalText) >= target) {
            return CorrectedColor(format(channels), false)
        }
        val delta = if (luminance(channels(canonicalText)) < DIRECTION_THRESHOLD) STEP else -STEP
        repeat(MAX_STEPS) {
            channels = channels.map { min(MAX_CHANNEL, max(MIN_CHANNEL, it + delta)) }
            if (contrastRatio(format(channels), canonicalText) >= target) {
                return CorrectedColor(format(channels), true)
            }
        }
        return CorrectedColor(format(channels), true)
    }

    fun contrastRatio(first: String, second: String): Double {
        val light = max(luminance(channels(first)), luminance(channels(second)))
        val dark = min(luminance(channels(first)), luminance(channels(second)))
        return (light + CONTRAST_OFFSET) / (dark + CONTRAST_OFFSET)
    }

    private fun channels(color: String): List<Int> =
        canonicalize(color).removePrefix("#").chunked(2).map { it.toInt(HEX_RADIX) }

    private fun luminance(channels: List<Int>): Double {
        val linear = channels.map { channel ->
            val value = channel / CHANNEL_MAX
            if (value <= SRGB_THRESHOLD) {
                value / SRGB_DIVISOR
            } else {
                ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
            }
        }
        return RED_WEIGHT * linear[0] + GREEN_WEIGHT * linear[1] + BLUE_WEIGHT * linear[2]
    }

    private fun format(channels: List<Int>): String = channels.joinToString("", "#") { "%02X".format(it) }
    private const val MIN_CHANNEL = 0
    private const val MAX_CHANNEL = 255
    private const val HEX_RADIX = 16
    private const val CHANNEL_MAX = 255.0
    private const val DIRECTION_THRESHOLD = 0.5
    private const val SRGB_THRESHOLD = 0.04045
    private const val SRGB_DIVISOR = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_SCALE = 1.055
    private const val SRGB_EXPONENT = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val CONTRAST_OFFSET = 0.05
    private const val STEP = 8
    private const val MAX_STEPS = 64
}
