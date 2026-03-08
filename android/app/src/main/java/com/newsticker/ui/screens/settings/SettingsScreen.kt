package com.newsticker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsticker.data.local.FeedSetting
import com.newsticker.ui.theme.AccentRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val feeds by viewModel.feeds.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feeds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                itemsIndexed(feeds) { index, feed ->
                    FeedRow(
                        index = index + 1,
                        feed = feed,
                        onUpdate = { viewModel.updateFeed(index, it) }
                    )
                    if (index < feeds.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Speichern", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun FeedRow(
    index: Int,
    feed: FeedSetting,
    onUpdate: (FeedSetting) -> Unit
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedBorderColor = AccentRed,
        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        cursorColor = AccentRed,
        focusedLabelColor = AccentRed,
        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Row 1: Checkbox + Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = feed.enabled,
                onCheckedChange = { onUpdate(feed.copy(enabled = it)) },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentRed,
                    uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            )

            Text(
                text = "$index.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.width(24.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = feed.name,
                onValueChange = { onUpdate(feed.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        // Row 2: URL (indented to align with name field)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 76.dp)
        ) {
            OutlinedTextField(
                value = feed.url,
                onValueChange = { onUpdate(feed.copy(url = it)) },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}
