package com.newsticker.data.repository

import com.newsticker.data.local.FeedConfig
import com.newsticker.data.local.ReadHistory
import com.newsticker.data.local.RssFetcher
import com.newsticker.data.model.Article

class NewsRepository {

    suspend fun fetchAllArticles(
        feeds: List<FeedConfig>,
        readHistory: ReadHistory
    ): Pair<List<Article>, List<String>> {
        return RssFetcher.fetchAllFeeds(feeds, readHistory)
    }
}
