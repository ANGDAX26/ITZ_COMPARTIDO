package com.itz.compartido.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Azul = Color(0xFF1B396A)
private val Dorado = Color(0xFFF2B705)

private val Esquema = lightColorScheme(
    primary = Azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E3F7),
    onPrimaryContainer = Color(0xFF0B1F3F),
    secondary = Dorado,
    onSecondary = Color(0xFF2B2100),
    secondaryContainer = Color(0xFFFFEFB8),
    onSecondaryContainer = Color(0xFF2B2100),
    error = Color(0xFFB3261E)
)

@Composable
fun ItzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, content = content)
}
