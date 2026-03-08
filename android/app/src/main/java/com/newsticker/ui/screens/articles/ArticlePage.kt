package com.newsticker.ui.screens.articles

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.newsticker.data.model.Article
import com.newsticker.ui.components.ArticleWebView
import com.newsticker.ui.components.ArticleWebViewUrl
import com.newsticker.ui.components.SourceBadge
import com.newsticker.ui.theme.AccentRed
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ArticlePage(
    article: Article,
    contentState: ContentState?,
    isLoadingContent: Boolean,
    onMarkRead: () -> Unit,
    onMarkReadAndOpenSameFeed: () -> Unit,
    onLoadContent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(article.link) {
        onLoadContent()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Source badge + date row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBadge(source = article.source)
            Text(
                text = formatDate(article.pubDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("In Browser lesen")
            }

            OutlinedButton(
                onClick = onMarkReadAndOpenSameFeed,
                modifier = Modifier.weight(1f)
            ) {
                Text("Gelesen gleicher Feed")
            }

            Button(
                onClick = onMarkRead,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text("Gelesen nächster Feed", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Article content
        when {
            isLoadingContent && contentState == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = AccentRed,
                            modifier = Modifier.height(24.dp).width(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Artikel wird geladen...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            contentState is ContentState.Html -> {
                ArticleWebView(
                    html = contentState.html,
                    baseUrl = contentState.baseUrl,
                    onBrowserClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                        )
                    },
                    onGelesenClick = onMarkRead,
                    onSameFeedClick = onMarkReadAndOpenSameFeed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            contentState is ContentState.DirectUrl -> {
                ArticleWebViewUrl(
                    url = contentState.url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            else -> {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val parsed = ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME)
        parsed.format(DateTimeFormatter.ofPattern("dd. MMM yyyy, HH:mm", Locale.GERMAN))
    } catch (_: Exception) {
        try {
            val parsed = ZonedDateTime.parse(dateStr)
            parsed.format(DateTimeFormatter.ofPattern("dd. MMM yyyy, HH:mm", Locale.GERMAN))
        } catch (_: Exception) {
            dateStr
        }
    }
}
