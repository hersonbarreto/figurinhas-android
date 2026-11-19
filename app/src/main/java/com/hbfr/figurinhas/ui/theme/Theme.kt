package com.hbfr.figurinhas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// App sempre escuro, como o iOS. Sem cor dinâmica (Material You) para manter a marca.
private val Scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF1A0B3D),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Cyan,
    onSecondary = Color(0xFF032B33),
    secondaryContainer = Color(0xFF0E4A57),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFF472B6),
    onTertiary = Color(0xFF3B0A24),
    background = Background,
    onBackground = OnSurface,
    surface = Background,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceLowest,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    surfaceTint = Color.Transparent, // sem véu de cor por elevação: cores previsíveis
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0909),
    scrim = Color(0xCC000000),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),   // miniaturas de figurinha
    medium = RoundedCornerShape(16.dp),  // botões de origem, campos
    large = RoundedCornerShape(24.dp),   // cartões de pacote, painéis
    extraLarge = RoundedCornerShape(28.dp), // prévia do editor, bottom sheets
)

@Composable
fun FigurinhasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
