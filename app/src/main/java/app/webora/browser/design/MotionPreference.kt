package app.webora.browser.design

import android.content.Context
import android.provider.Settings

/** Reads the browser/platform animation preference; unavailable state conservatively disables motion. */
internal fun reducedMotionEnabled(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
            ?.toFloatOrNull()
    }.getOrNull()
    return reducedMotionEnabled(scale)
}

internal fun reducedMotionEnabled(animatorDurationScale: Float?): Boolean =
    animatorDurationScale == null || !animatorDurationScale.isFinite() || animatorDurationScale <= 0f
