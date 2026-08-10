package app.webora.browser.web

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import app.webora.browser.browser.ExternalNavigation
import java.net.URI

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

internal fun launchExternalUrl(context: Context, url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull()?.takeIf { it.scheme == "https" && it.host != null }
        ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toASCIIString()))
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

internal fun sharePage(context: Context, pageUrl: String): Boolean {
    val uri = runCatching { URI(pageUrl) }.getOrNull()?.takeIf {
        it.isAbsolute && (it.scheme == "http" || it.scheme == "https") && it.host != null
    } ?: return false
    val intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, uri.toASCIIString()),
        null,
    )
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
