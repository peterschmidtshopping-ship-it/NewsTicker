package com.newsticker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readStore: DataStore<Preferences> by preferencesDataStore(name = "read_articles")

class ReadStore(private val context: Context) {

    private val readUrlsKey = stringSetPreferencesKey("read_urls")

    private companion object {
        const val MAX_ENTRIES = 5000
    }

    fun readUrls(): Flow<Set<String>> = context.readStore.data.map { prefs ->
        prefs[readUrlsKey] ?: emptySet()
    }

    suspend fun markRead(url: String) {
        context.readStore.edit { prefs ->
            val current = prefs[readUrlsKey]?.toMutableSet() ?: mutableSetOf()
            current.add(url)
            // Cap at MAX_ENTRIES — drop oldest (arbitrary since Set has no order,
            // but keeps the size bounded)
            if (current.size > MAX_ENTRIES) {
                val excess = current.size - MAX_ENTRIES
                val iter = current.iterator()
                repeat(excess) {
                    iter.next()
                    iter.remove()
                }
            }
            prefs[readUrlsKey] = current
        }
    }
}
