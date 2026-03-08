package com.newsticker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.newsticker.ui.theme.sourceColor

@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    Text(
        text = source,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(sourceColor(source))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
