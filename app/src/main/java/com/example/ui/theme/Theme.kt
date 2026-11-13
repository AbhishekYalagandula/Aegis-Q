package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SyntaxColors(
  val key: Color,
  val stringValue: Color,
  val numberValue: Color,
  val booleanValue: Color,
  val nullValue: Color,
  val brace: Color,
  val elevatedCard: Color,
)

private val DarkSyntaxColors =
  SyntaxColors(
    key = Color(0xFF5EEAD4),
    stringValue = Color(0xFF86EFAC),
    numberValue = Color(0xFFFBBF24),
    booleanValue = Color(0xFF38BDF8),
    nullValue = Color(0xFF94A3B8),
    brace = Color(0xFFCBD5E1),
    elevatedCard = DarkSurfaceElevated,
  )

private val LightSyntaxColors =
  SyntaxColors(
    key = Color(0xFF0F766E),
    stringValue = Color(0xFF15803D),
    numberValue = Color(0xFFB45309),
    booleanValue = Color(0xFF0369A1),
    nullValue = Color(0xFF64748B),
    brace = Color(0xFF334155),
    elevatedCard = LightSurfaceElevated,
  )

val LocalSyntaxColors = staticCompositionLocalOf { LightSyntaxColors }

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
  )

@Composable
fun AccessLogTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val syntaxColors = if (darkTheme) DarkSyntaxColors else LightSyntaxColors

  CompositionLocalProvider(LocalSyntaxColors provides syntaxColors) {
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
  }
}
