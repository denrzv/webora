package app.webora.browser.browser

import android.content.Context
import dev.siteskin.core.origin.SiteOrigin
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class BrowsingRecord(
    val url: String,
    val origin: String,
    val title: String,
    val visitedAtMillis: Long,
    val sequence: Long,
)

internal interface BrowsingRecordPreferences {
    fun history(): String?
    fun favourites(): String?
    fun saveHistory(value: String)
    fun saveFavourites(value: String)
}

internal class BrowsingRecordStore(
    private val preferences: BrowsingRecordPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(SharedBrowsingRecordPreferences(context))

    private var nextSequence = 1L

    fun recordVisit(url: String, title: String?): Boolean {
        val record = newRecord(url, title) ?: return false
        val updated = (listOf(record) + history()).take(MAX_HISTORY)
        preferences.saveHistory(BrowsingRecordCodec.encodeAll(updated))
        return true
    }

    fun history(): List<BrowsingRecord> = decoded(preferences.history(), MAX_HISTORY)

    fun recentSites(): List<BrowsingRecord> = history()
        .sortedWith(RECORD_ORDER)
        .distinctBy(BrowsingRecord::url)
        .take(MAX_RECENTS)

    fun favourites(): List<BrowsingRecord> = decoded(preferences.favourites(), MAX_FAVOURITES)
        .sortedWith(RECORD_ORDER)

    fun addFavourite(url: String, title: String?): Boolean {
        val record = newRecord(url, title) ?: return false
        val updated = (listOf(record) + favourites().filterNot { it.url == record.url })
            .take(MAX_FAVOURITES)
        preferences.saveFavourites(BrowsingRecordCodec.encodeAll(updated))
        return true
    }

    fun removeFavourite(url: String): Boolean {
        val canonical = canonicalBrowsingUrl(url) ?: return false
        val current = favourites()
        val updated = current.filterNot { it.url == canonical }
        if (updated.size == current.size) return false
        preferences.saveFavourites(BrowsingRecordCodec.encodeAll(updated))
        return true
    }

    fun isFavourite(url: String): Boolean {
        val canonical = canonicalBrowsingUrl(url) ?: return false
        return favourites().any { it.url == canonical }
    }

    fun clearHistory() = preferences.saveHistory(BrowsingRecordCodec.encodeAll(emptyList()))

    private fun newRecord(url: String, title: String?): BrowsingRecord? {
        val canonical = canonicalBrowsingUrl(url) ?: return null
        val origin = SiteOrigin.parse(canonical) ?: return null
        val sequence = maxOf(nextSequence, maxStoredSequence() + 1)
        nextSequence = sequence + 1
        return BrowsingRecord(
            url = canonical,
            origin = origin.canonical,
            title = sanitizedBrowsingTitle(title).ifEmpty { origin.host },
            visitedAtMillis = clock().coerceAtLeast(0L),
            sequence = sequence,
        )
    }

    private fun maxStoredSequence(): Long =
        (history() + favourites()).maxOfOrNull(BrowsingRecord::sequence) ?: 0L

    private fun decoded(value: String?, limit: Int): List<BrowsingRecord> =
        BrowsingRecordCodec.decodeAll(value, limit)

    companion object {
        const val MAX_HISTORY = 200
        const val MAX_RECENTS = 10
        const val MAX_FAVOURITES = 100
        const val MAX_TITLE_LENGTH = 128
        const val MAX_URL_LENGTH = 2048

        private val RECORD_ORDER = compareByDescending<BrowsingRecord> { it.visitedAtMillis }
            .thenByDescending { it.sequence }
            .thenBy(BrowsingRecord::url)
    }
}

internal fun canonicalBrowsingUrl(value: String): String? {
    val parsed = parseBrowsingUri(value) ?: return null
    val origin = SiteOrigin.parse(value) ?: return null
    val path = parsed.normalize().rawPath.orEmpty().ifEmpty { "/" }
    if (path.split('/').any { it == ".." }) return null
    val port = origin.port.takeUnless {
        (origin.scheme == "https" && it == HTTPS_PORT) || (origin.scheme == "http" && it == HTTP_PORT)
    } ?: NO_PORT
    return runCatching {
        URI(origin.scheme, null, origin.host.removeSurrounding("[", "]"), port, path, parsed.rawQuery, null)
            .toASCIIString()
    }.getOrNull()?.takeIf { it.length <= BrowsingRecordStore.MAX_URL_LENGTH }
}

private fun parseBrowsingUri(value: String): URI? {
    val bounded = value.length in 1..BrowsingRecordStore.MAX_URL_LENGTH && !value.startsWith("//")
    return value.takeIf { bounded }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?.takeIf { it.userInfo == null }
}

internal fun sanitizedBrowsingTitle(value: String?): String = value.orEmpty()
    .map { if (Character.isISOControl(it)) ' ' else it }
    .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
    .joinToString("")
    .trim()
    .replace(Regex("\\s+"), " ")
    .take(BrowsingRecordStore.MAX_TITLE_LENGTH)

internal object BrowsingRecordCodec {
    private const val VERSION = "1"
    private const val SEPARATOR = '\t'

    fun encode(record: BrowsingRecord): String = listOf(
        record.url,
        record.origin,
        record.title,
        record.visitedAtMillis.toString(),
        record.sequence.toString(),
    ).joinToString(SEPARATOR.toString()) { encodeField(it) }

    fun encodeAll(records: List<BrowsingRecord>): String =
        (listOf(VERSION) + records.map(::encode)).joinToString("\n")

    fun decodeAll(value: String?, limit: Int): List<BrowsingRecord> {
        val lines = value?.lineSequence()?.toList() ?: return emptyList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).take(limit).mapNotNull(::decode)
    }

    private fun decode(line: String): BrowsingRecord? {
        val fields = line.split(SEPARATOR).map { decodeField(it) ?: return null }
        return fields.takeIf { it.size == FIELD_COUNT }?.let(::decodedRecord)
    }

    private fun decodedRecord(fields: List<String>): BrowsingRecord? {
        val url = canonicalBrowsingUrl(fields[URL_FIELD]) ?: return null
        val origin = SiteOrigin.parse(url)?.canonical
        val title = fields[TITLE_FIELD]
        val validIdentity = fields[ORIGIN_FIELD] == origin
        val validTitle = title.isNotEmpty() && title == sanitizedBrowsingTitle(title)
        val time = fields[TIME_FIELD].toLongOrNull()?.takeIf { it >= 0 }
        val sequence = fields[SEQUENCE_FIELD].toLongOrNull()?.takeIf { it > 0 }
        val validStoredValues = time != null && sequence != null
        return if (validIdentity && validTitle && validStoredValues) {
            BrowsingRecord(url, requireNotNull(origin), title, time, sequence)
        } else {
            null
        }
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private const val FIELD_COUNT = 5
    private const val URL_FIELD = 0
    private const val ORIGIN_FIELD = 1
    private const val TITLE_FIELD = 2
    private const val TIME_FIELD = 3
    private const val SEQUENCE_FIELD = 4
}

private class SharedBrowsingRecordPreferences(context: Context) : BrowsingRecordPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun history(): String? = preferences.getString(HISTORY, null)
    override fun favourites(): String? = preferences.getString(FAVOURITES, null)
    override fun saveHistory(value: String) { preferences.edit().putString(HISTORY, value).apply() }
    override fun saveFavourites(value: String) { preferences.edit().putString(FAVOURITES, value).apply() }
}

private const val PREFERENCES_NAME = "webora_browsing_records"
private const val HISTORY = "history_v1"
private const val FAVOURITES = "favourites_v1"
private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
private const val NO_PORT = -1
