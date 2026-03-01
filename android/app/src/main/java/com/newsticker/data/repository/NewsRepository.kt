package com.newsticker.data.repository

import com.newsticker.data.local.RssFetcher
import com.newsticker.data.model.Article

class NewsRepository {

    suspend fun fetchAllArticles(readUrls: Set<String>): Pair<List<Article>, List<String>> {
        return RssFetcher.fetchAllFeeds(readUrls)
    }
}
