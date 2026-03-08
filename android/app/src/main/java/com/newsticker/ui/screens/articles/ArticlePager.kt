package com.newsticker.ui.screens.articles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.newsticker.data.model.Article
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ArticlePager(
    articles: List<Article>,
    pagerState: PagerState,
    contentCacheFlow: StateFlow<Map<String, ContentState>>,
    loadingContentFlow: StateFlow<Set<String>>,
    onMarkRead: (Article) -> Unit,
    onMarkReadAndOpenSameFeed: (Article) -> Unit,
    onLoadContent: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Single subscription outside pager — no per-page flow overhead.
    // Not using `by` so .value is NOT read here (no scope invalidation).
    val contentCacheState = contentCacheFlow.collectAsState()
    val loadingContentState = loadingContentFlow.collectAsState()

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        if (page < articles.size) {
            val article = articles[page]

            // derivedStateOf only notifies when THIS article's value changes
            val contentState by remember(article.link) {
                derivedStateOf { contentCacheState.value[article.link] }
            }
            val isLoading by remember(article.link) {
                derivedStateOf { loadingContentState.value.contains(article.link) }
            }

            // Stable lambda references so ArticlePage can skip recomposition
            val stableMarkRead = remember(article.link) { { onMarkRead(article) } }
            val stableMarkReadAndOpenSameFeed = remember(article.link) {
                { onMarkReadAndOpenSameFeed(article) }
            }
            val stableLoadContent = remember(article.link) {
                { onLoadContent(article.link, article.imageUrl, article.title) }
            }

            ArticlePage(
                article = article,
                contentState = contentState,
                isLoadingContent = isLoading,
                onMarkRead = stableMarkRead,
                onMarkReadAndOpenSameFeed = stableMarkReadAndOpenSameFeed,
                onLoadContent = stableLoadContent
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
