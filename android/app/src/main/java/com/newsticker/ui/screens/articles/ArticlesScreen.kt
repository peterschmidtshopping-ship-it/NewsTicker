package com.newsticker.ui.screens.articles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsticker.ui.theme.AccentRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ArticlesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val contentCache by viewModel.contentCache.collectAsState()
    val loadingContent by viewModel.loadingContent.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val subtitle = when (val state = uiState) {
                        is UiState.Loaded -> "${state.articles.size} Artikel"
                        else -> ""
                    }
                    Column {
                        Text("NewsTicker")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadArticles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is UiState.Idle -> {
                    // Should not normally stay here
                }

                is UiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Artikel werden geladen...",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = AccentRed,
                            trackColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                }

                is UiState.Loaded -> {
                    if (state.articles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Keine passenden Artikel gefunden",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        val pagerState = rememberPagerState(
                            initialPage = state.currentPage.coerceIn(0, state.articles.size - 1),
                            pageCount = { state.articles.size }
                        )

                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.currentPage }.collect { page ->
                                viewModel.updateCurrentPage(page)
                            }
                        }

                        // Sync pager when articles are removed
                        LaunchedEffect(state.articles.size, state.currentPage) {
                            val target = state.currentPage.coerceIn(0, state.articles.size - 1)
                            if (pagerState.currentPage != target) {
                                pagerState.scrollToPage(target)
                            }
                        }

                        ArticlePager(
                            articles = state.articles,
                            pagerState = pagerState,
                            contentCache = contentCache,
                            loadingContent = loadingContent,
                            onMarkRead = { article -> viewModel.markRead(article) },
                            onLoadContent = { url, imageUrl, title -> viewModel.loadArticleContent(url, imageUrl, title) }
                        )

                        // Page indicator
                        Text(
                            text = "${state.currentPage + 1} / ${state.articles.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Fehler",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
