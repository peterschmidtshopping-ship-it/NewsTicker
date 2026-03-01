package com.newsticker.ui.screens.articles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class ArticlesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NewsRepository()
    private val readStore = ReadStore(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadArticles()
    }

    fun loadArticles() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val readUrls = readStore.readUrls().first()
                val (articles, warnings) = repository.fetchAllArticles(readUrls)
                _uiState.value = UiState.Loaded(articles = articles, warnings = warnings)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.message ?: "Fehler beim Laden der Feeds"
                )
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
                    val newPage = current.currentPage.coerceAtMost(updated.size - 1)
                    _uiState.value = current.copy(articles = updated, currentPage = newPage)
                }
            }
        }
    }

    fun updateCurrentPage(page: Int) {
        val current = _uiState.value
        if (current is UiState.Loaded) {
            _uiState.value = current.copy(currentPage = page)
        }
    }
}
