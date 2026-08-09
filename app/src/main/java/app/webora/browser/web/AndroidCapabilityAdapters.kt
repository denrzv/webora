package app.webora.browser.web

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import app.webora.browser.browser.ExternalNavigation

internal fun externalIntent(navigation: ExternalNavigation): Intent = when (navigation.kind) {
    ExternalNavigation.Kind.EMAIL -> Intent(Intent.ACTION_SENDTO)
    ExternalNavigation.Kind.TELEPHONE -> Intent(Intent.ACTION_DIAL)
    ExternalNavigation.Kind.MAP -> Intent(Intent.ACTION_VIEW)
}.setData(Uri.parse(navigation.uri))

internal fun launchExternal(context: Context, navigation: ExternalNavigation): Boolean {
    val intent = externalIntent(navigation)
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

internal fun enqueueDownload(context: Context, untrustedUrl: String): Boolean {
    val url = downloadUrl(untrustedUrl) ?: return false
    val request = DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            URLUtil.guessFileName(url, null, null),
        )
    val manager = context.getSystemService(DownloadManager::class.java) ?: return false
    return runCatching { manager.enqueue(request) }.isSuccess
}
