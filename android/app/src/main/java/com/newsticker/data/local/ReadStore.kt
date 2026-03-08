package com.newsticker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URI

private val Context.readStore: DataStore<Preferences> by preferencesDataStore(name = "read_articles")

data class ReadHistory(
    val urls: Set<String>,
    val titles: Set<String>
)

class ReadStore(private val context: Context) {

    private val readUrlsKey = stringSetPreferencesKey("read_urls")
    private val readTitlesKey = stringSetPreferencesKey("read_titles")

    fun readHistory(): Flow<ReadHistory> = context.readStore.data.map { prefs ->
        ReadHistory(
            urls = prefs[readUrlsKey] ?: emptySet(),
            titles = prefs[readTitlesKey] ?: emptySet()
        )
    }

    suspend fun markRead(url: String, title: String) {
        context.readStore.edit { prefs ->
            val currentUrls = prefs[readUrlsKey]?.toMutableSet() ?: mutableSetOf()
            val currentTitles = prefs[readTitlesKey]?.toMutableSet() ?: mutableSetOf()

            ReadStore.normalizeUrl(url).takeIf { it.isNotEmpty() }?.let(currentUrls::add)
            ReadStore.normalizeTitle(title).takeIf { it.isNotEmpty() }?.let(currentTitles::add)

            trimToMaxEntries(currentUrls)
            trimToMaxEntries(currentTitles)

            prefs[readUrlsKey] = currentUrls
            prefs[readTitlesKey] = currentTitles
        }
    }

    private fun trimToMaxEntries(values: MutableSet<String>) {
        if (values.size <= MAX_ENTRIES) return
        val excess = values.size - MAX_ENTRIES
        val iter = values.iterator()
        repeat(excess) {
            iter.next()
            iter.remove()
        }
    }

    companion object {
        private const val MAX_ENTRIES = 5000

        fun normalizeUrl(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return ""

            return try {
                val parsed = URI(trimmed)
                val scheme = parsed.scheme?.lowercase() ?: ""
                val host = parsed.host?.lowercase() ?: ""
                val port = when {
                    parsed.port == -1 -> ""
                    scheme == "https" && parsed.port == 443 -> ""
                    scheme == "http" && parsed.port == 80 -> ""
                    else -> ":${parsed.port}"
                }
                val path = parsed.path.orEmpty().replace(Regex("/+$"), "").ifEmpty { "/" }
                val query = parsed.rawQuery?.let { "?$it" } ?: ""
                if (scheme.isEmpty() || host.isEmpty()) {
                    trimmed.replace(Regex("#.*$"), "").replace(Regex("/+$"), "")
                } else {
                    "$scheme://$host$port$path$query"
                }
            } catch (_: Exception) {
                trimmed.replace(Regex("#.*$"), "").replace(Regex("/+$"), "")
            }
        }

        fun normalizeTitle(title: String): String =
            title.trim().replace(Regex("\\s+"), " ").lowercase()
    }
}
