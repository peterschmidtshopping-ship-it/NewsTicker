package com.newsticker.ui.theme

import androidx.compose.ui.graphics.Color

val DarkNavy = Color(0xFF1A1A2E)
val MidNavy = Color(0xFF16213E)
val DarkSurface = Color(0xFF0F3460)
val AccentRed = Color(0xFFE94560)

val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFFB0B0B0)

// Source badge colors (matching web CSS)
val SourceHeise = Color(0xFFE94560)
val SourceSpiegel = Color(0xFFE64415)
val SourceGamestar = Color(0xFF1A73E8)
val SourceFaz = Color(0xFF2A5298)
val SourceHandelsblatt = Color(0xFFC24B1A)
val SourceTagesschau = Color(0xFF004B76)

fun sourceColor(source: String): Color = when (source.lowercase()) {
    "heise" -> SourceHeise
    "spiegel" -> SourceSpiegel
    "gamestar" -> SourceGamestar
    "faz" -> SourceFaz
    "handelsblatt" -> SourceHandelsblatt
    "tagesschau" -> SourceTagesschau
    else -> AccentRed
}
