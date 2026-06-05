package com.mangalens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
	primary = BluePrimary,
	secondary = BlueSecondary,
	tertiary = BlueTertiary
)

private val DarkColors = darkColorScheme(
	primary = BluePrimary,
	secondary = BlueSecondary,
	tertiary = BlueTertiary
)

@Composable
fun MangaLensTheme(
	useDarkTheme: Boolean = false,
	content: @Composable () -> Unit
) {
	val colors = if (useDarkTheme) DarkColors else LightColors

	MaterialTheme(
		colorScheme = colors,
		typography = MangaLensTypography,
		content = content
	)
}
