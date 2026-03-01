package com.newsticker.ui.screens.articles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newsticker.data.local.ArticleContent
import com.newsticker.data.local.ArticleFetcher
import com.newsticker.data.local.FeedConfig
import com.newsticker.data.local.FeedStore
import com.newsticker.data.local.ReadStore
import com.newsticker.data.model.Article
import com.newsticker.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Loaded(
        val articles: List<Article>,
        val warnings: List<String> = emptyList(),
        val currentPage: Int = 0
    ) : UiState()
    data class Error(val message: String) : UiState()
}

/** How to display article content */
sealed class ContentState {
    /** Extracted HTML content to render via loadDataWithBaseURL */
    data class Html(val html: String, val baseUrl: String) : ContentState()
    /** Load the URL directly in WebView (fallback for bot-protected sites) */
    data class DirectUrl(val url: String) : ContentState()
}

class ArticlesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NewsRepository()
    private val readStore = ReadStore(application)
    private val feedStore = FeedStore(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _contentCache = MutableStateFlow<Map<String, ContentState>>(emptyMap())
    val contentCache: StateFlow<Map<String, ContentState>> = _contentCache

    private val _loadingContent = MutableStateFlow<Set<String>>(emptySet())
    val loadingContent: StateFlow<Set<String>> = _loadingContent

    /** Tracked locally for markRead positioning — NOT in _uiState to avoid recomposition on every swipe */
    private var _currentPage: Int = 0

    init {
        loadArticles()
    }

    fun loadArticles() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val feedSettings = feedStore.feeds().first()
                val feeds = feedSettings
                    .filter { it.enabled && it.url.isNotBlank() }
                    .map { FeedConfig(it.url, it.name) }
                val readUrls = readStore.readUrls().first()
                val (articles, warnings) = repository.fetchAllArticles(feeds, readUrls)
                _uiState.value = UiState.Loaded(articles = articles, warnings = warnings)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.message ?: "Fehler beim Laden der Feeds"
                )
            }
        }
    }

    fun loadArticleContent(articleUrl: String, imageUrl: String = "", title: String = "") {
        if (_contentCache.value.containsKey(articleUrl)) return
        if (_loadingContent.value.contains(articleUrl)) return

        viewModelScope.launch {
            _loadingContent.value = _loadingContent.value + articleUrl
            try {
                val result = ArticleFetcher.fetchArticle(articleUrl, imageUrl, title)
                val state = when (result) {
                    is ArticleContent.Html -> ContentState.Html(result.html, articleUrl)
                    is ArticleContent.LoadUrl -> ContentState.DirectUrl(result.url)
                }
                _contentCache.value = _contentCache.value + (articleUrl to state)
            } catch (_: Exception) {
                _contentCache.value = _contentCache.value + (articleUrl to ContentState.DirectUrl(articleUrl))
            } finally {
                _loadingContent.value = _loadingContent.value - articleUrl
            }
        }
    }

    fun markRead(article: Article) {
        viewModelScope.launch {
            readStore.markRead(article.link)

            val current = _uiState.value
            if (current is UiState.Loaded) {
                val updated = current.articles.filter { it.link != article.link }
                if (updated.isEmpty()) {
                    _uiState.value = UiState.Loaded(articles = emptyList(), currentPage = 0)
                } else {
                    val newPage = _currentPage.coerceAtMost(updated.size - 1)
                    _uiState.value = current.copy(articles = updated, currentPage = newPage)
                }
            }
        }
    }

    fun updateCurrentPage(page: Int) {
        _currentPage = page
    }
}
