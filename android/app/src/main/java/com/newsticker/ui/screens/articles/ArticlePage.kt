package com.newsticker.ui.screens.articles

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.newsticker.data.model.Article
import com.newsticker.ui.components.ArticleWebView
import com.newsticker.ui.components.SourceBadge
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ArticlePage(
    article: Article,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var contentExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Source badge + date row
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = article.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { contentExpanded = !contentExpanded },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (contentExpanded) "Schließen" else "Artikel lesen")
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Browser")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onMarkRead,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Text("Gelesen", color = MaterialTheme.colorScheme.onSurface)
        }

        // Expandable WebView content — loads article URL directly
        AnimatedVisibility(
            visible = contentExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ArticleWebView(
                url = article.link,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp)
                    .padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
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
