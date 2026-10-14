package de.lennart.minilauncher.ui

import android.graphics.Typeface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val DimText = Color(0xFF8A8A8A)
val SurfaceColor = Color(0xFF141414)
val DangerColor = Color(0xFFFF6B6B)

private val LauncherColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = DimText,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = SurfaceColor,
    onSurfaceVariant = DimText,
    surfaceContainerHigh = SurfaceColor,
    outline = DimText,
)

/** One modular type scale (ratio ~1.27) used across all screens. */
object TypeScale {
    val Title = 26.sp
    val Body = 20.sp
    val Caption = 15.sp
    val Small = 13.sp
}

/**
 * The device's own UI font: Google Sans on Pixels (as used by the system UI), otherwise the
 * default sans-serif. Unknown family names resolve to [Typeface.DEFAULT].
 */
val SystemFont: FontFamily by lazy {
    listOf("google-sans-flex", "google-sans", "google-sans-text")
        .map { Typeface.create(it, Typeface.NORMAL) }
        .firstOrNull { it != Typeface.DEFAULT }
        ?.let { FontFamily(it) }
        ?: FontFamily.Default
}

private fun Typography.withFont(font: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = font)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LauncherColors,
        typography = Typography().withFont(SystemFont),
        content = content,
    )
}

/** Click handling without ripple, to keep the UI text-only. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.plainClickable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    combinedClickable(
        interactionSource = null,
        indication = null,
        onLongClick = onLongClick,
        onClick = onClick,
    )
