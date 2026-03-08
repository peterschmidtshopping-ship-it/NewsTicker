package com.newsticker.data.local

import com.newsticker.data.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class FeedConfig(val url: String, val source: String)

object RssFetcher {

    private const val ARTICLES_PER_FEED = 10

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAllFeeds(
        feeds: List<FeedConfig>,
        readUrls: Set<String> = emptySet()
    ): Pair<List<Article>, List<String>> = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()

        val results = feeds.map { feed ->
            async {
                try {
                    fetchFeed(feed, readUrls)
                } catch (e: Exception) {
                    val message = e.message ?: e.toString()
                    synchronized(warnings) {
                        warnings.add("${feed.source}: $message")
                    }
                    emptyList()
                }
            }
        }.awaitAll()

        Pair(interleave(results), warnings)
    }

    private fun fetchFeed(feed: FeedConfig, readUrls: Set<String>): List<Article> {
        val request = Request.Builder()
            .url(feed.url)
            .header("User-Agent", "NewsTicker/1.0")
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            response.body?.string() ?: throw Exception("Empty response")
        }

        return parseRss(responseBody, feed.source, readUrls)
    }

    private fun parseRss(xml: String, source: String, readUrls: Set<String>): List<Article> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val articles = mutableListOf<Article>()
        var inItem = false
        var title = ""
        var link = ""
        var description = ""
        var contentEncoded = ""
        var pubDate = ""
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item" || currentTag == "entry") {
                        inItem = true
                        title = ""
                        link = ""
                        description = ""
                        contentEncoded = ""
                        pubDate = ""
                    }
                    // Atom feeds use <link href="..."/>
                    if (inItem && currentTag == "link" && parser.getAttributeValue(null, "href") != null) {
                        link = parser.getAttributeValue(null, "href")
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title" -> title += text
                            "link" -> if (link.isEmpty()) link += text
                            "description", "summary" -> description += text
                            "content:encoded" -> contentEncoded += text
                            "pubDate", "published", "updated", "dc:date" -> pubDate += text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" || parser.name == "entry") {
                        inItem = false
                        if (link.isNotEmpty() && !readUrls.contains(link) && articles.size < ARTICLES_PER_FEED) {
                            // Extract image URL from content:encoded (if available)
                            val imageUrl = Regex("""<img[^>]+src="([^"]+)"""")
                                .find(contentEncoded)?.groupValues?.get(1) ?: ""

                            // Use content:encoded for description if description is empty
                            val rawDescription = description.ifEmpty { contentEncoded }

                            // Strip HTML tags from description
                            val cleanDescription = rawDescription
                                .replace(Regex("<[^>]*>"), "")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&#039;", "'")
                                .trim()

                            articles.add(
                                Article(
                                    title = title.trim(),
                                    description = cleanDescription,
                                    link = link.trim(),
                                    pubDate = pubDate.trim(),
                                    source = source,
                                    imageUrl = imageUrl
                                )
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }

        return articles
    }

    private fun interleave(arrays: List<List<Article>>): List<Article> {
        val result = mutableListOf<Article>()
        val maxLen = arrays.maxOfOrNull { it.size } ?: 0

        for (i in 0 until maxLen) {
            for (arr in arrays) {
                if (i < arr.size) {
                    result.add(arr[i])
                }
            }
        }

        return result
    }
}
