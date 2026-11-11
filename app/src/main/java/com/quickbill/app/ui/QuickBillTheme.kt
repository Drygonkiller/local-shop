package com.quickbill.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QuickBillColors = lightColorScheme(
    primary = Color(0xFF145B48),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE5),
    onPrimaryContainer = Color(0xFF174636),
    secondary = Color(0xFF4D665B),
    secondaryContainer = Color(0xFFE6EFE9),
    onSecondaryContainer = Color(0xFF253D32),
    background = Color(0xFFF7F8F3),
    onBackground = Color(0xFF192C24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF192C24),
    surfaceVariant = Color(0xFFE8EFEA),
    onSurfaceVariant = Color(0xFF4B6257),
    outline = Color(0xFF70877B),
    outlineVariant = Color(0xFFD3DFD7),
    surfaceContainer = Color(0xFFF0F4EF),
    surfaceContainerHigh = Color(0xFFE9F0EB),
    error = Color(0xFFBA1A1A),
)

@Composable
fun QuickBillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QuickBillColors, typography = Typography(), content = content)
}
