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
    onMarkRead: (Article) -> Unit,
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
                onMarkRead = { onMarkRead(article) }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
