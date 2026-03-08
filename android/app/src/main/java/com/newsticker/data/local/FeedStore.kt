package com.newsticker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class FeedSetting(
    val enabled: Boolean,
    val name: String,
    val url: String
)

private val Context.feedStore: DataStore<Preferences> by preferencesDataStore(name = "feeds")

class FeedStore(private val context: Context) {

    private val feedsKey = stringPreferencesKey("feeds_json")

    companion object {
        const val MAX_FEEDS = 10

        fun loadDefaultFeeds(context: Context): List<FeedSetting> {
            val json = context.assets.open("feeds.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.getJSONArray("feeds")
            val list = mutableListOf<FeedSetting>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FeedSetting(
                        enabled = obj.optBoolean("enabled", false),
                        name = obj.optString("name", ""),
                        url = obj.optString("url", "")
                    )
                )
            }
            while (list.size < MAX_FEEDS) {
                list.add(FeedSetting(false, "", ""))
            }
            return list.take(MAX_FEEDS)
        }
    }

    private val defaultFeeds: List<FeedSetting> by lazy { loadDefaultFeeds(context) }

    fun feeds(): Flow<List<FeedSetting>> = context.feedStore.data.map { prefs ->
        val json = prefs[feedsKey]
        if (json != null) {
            deserialize(json)
        } else {
            defaultFeeds
        }
    }

    suspend fun saveFeeds(feeds: List<FeedSetting>) {
        context.feedStore.edit { prefs ->
            prefs[feedsKey] = serialize(feeds)
        }
    }

    private fun serialize(feeds: List<FeedSetting>): String {
        val arr = JSONArray()
        for (feed in feeds) {
            val obj = JSONObject()
            obj.put("enabled", feed.enabled)
            obj.put("name", feed.name)
            obj.put("url", feed.url)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<FeedSetting> {
        val arr = JSONArray(json)
        val list = mutableListOf<FeedSetting>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                FeedSetting(
                    enabled = obj.optBoolean("enabled", false),
                    name = obj.optString("name", ""),
                    url = obj.optString("url", "")
                )
            )
        }
        // Pad to MAX_FEEDS if needed
        while (list.size < MAX_FEEDS) {
            list.add(FeedSetting(false, "", ""))
        }
        return list.take(MAX_FEEDS)
    }
}
