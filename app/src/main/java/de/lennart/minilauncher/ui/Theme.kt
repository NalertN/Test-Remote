package de.lennart.minilauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Gray = Color(0xFF8A8A8A)

private val LauncherColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Gray,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Gray,
    surfaceContainerHigh = Color(0xFF111111),
    outline = Gray,
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LauncherColors, content = content)
}

val DimText = Gray

/** Click handling without ripple, to keep the UI text-only. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.plainClickable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    combinedClickable(
        interactionSource = null,
        indication = null,
        onLongClick = onLongClick,
        onClick = onClick,
    )
