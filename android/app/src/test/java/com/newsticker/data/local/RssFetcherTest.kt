package com.newsticker.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RssFetcherTest {

    @Test
    fun `takeUnreadUpToLimit skips read entries and keeps later unread ones from same feed`() {
        val sourceArticles = (1..15).map { article(it) }
        val readHistory = ReadHistory(
            urls = (1..10).map { "https://example.com/$it" }.toSet(),
            titles = emptySet()
        )

        val articles = RssFetcher.takeUnreadUpToLimit(sourceArticles, readHistory)

        assertEquals(5, articles.size)
        assertEquals("Article 11", articles.first().title)
        assertEquals("Article 15", articles.last().title)
        assertFalse(articles.any { it.title == "Article 1" })
    }

    @Test
    fun `takeUnreadUpToLimit caps unread results at articles per feed after skipping read entries`() {
        val sourceArticles = (1..25).map { article(it) }
        val readHistory = ReadHistory(
            urls = (1..5).map { "https://example.com/$it" }.toSet(),
            titles = emptySet()
        )

        val articles = RssFetcher.takeUnreadUpToLimit(sourceArticles, readHistory)

        assertEquals(10, articles.size)
        assertEquals("Article 6", articles.first().title)
        assertEquals("Article 15", articles.last().title)
    }

    private fun article(index: Int) = com.newsticker.data.model.Article(
        title = "Article $index",
        description = "Description $index",
        link = "https://example.com/$index",
        pubDate = "2026-03-09",
        source = "example"
    )
}
