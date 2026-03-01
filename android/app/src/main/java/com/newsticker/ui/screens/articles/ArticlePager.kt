package com.newsticker.ui.screens.articles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.newsticker.data.model.Article

@Composable
fun ArticlePager(
    articles: List<Article>,
    pagerState: PagerState,
    contentCache: Map<String, ContentState>,
    loadingContent: Set<String>,
    onMarkRead: (Article) -> Unit,
    onLoadContent: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        if (page < articles.size) {
            val article = articles[page]
            ArticlePage(
                article = article,
                contentState = contentCache[article.link],
                isLoadingContent = loadingContent.contains(article.link),
                onMarkRead = { onMarkRead(article) },
                onLoadContent = { onLoadContent(article.link, article.imageUrl, article.title) }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
