package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lennart.minilauncher.BuildConfig
import de.lennart.minilauncher.data.AppEntry

@Composable
fun SettingsScreen(
    favorites: List<AppEntry>,
    hiddenApps: List<AppEntry>,
    onMoveFavorite: (AppEntry, Int) -> Unit,
    onRemoveFavorite: (AppEntry) -> Unit,
    onUnhide: (AppEntry) -> Unit,
    onStartActivity: (Intent, Intent?) -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
                ActionText("Close", onClick = onClose)
            }
        }

        item {
            Section("Favorites") {
                if (favorites.isEmpty()) Hint("Long-press an app to add it.")
                favorites.forEachIndexed { index, app ->
                    AppRow(app.label) {
                        ActionText("↑", enabled = index > 0) { onMoveFavorite(app, -1) }
                        ActionText("↓", enabled = index < favorites.lastIndex) { onMoveFavorite(app, 1) }
                        ActionText("Remove", dim = true) { onRemoveFavorite(app) }
                    }
                }
            }
        }

        item {
            Section("Hidden apps") {
                if (hiddenApps.isEmpty()) Hint("No hidden apps.")
                hiddenApps.forEach { app ->
                    AppRow(app.label) {
                        ActionText("Unhide", dim = true) { onUnhide(app) }
                    }
                }
            }
        }

        item {
            Section("System") {
                Text(
                    "Choose default launcher",
                    color = Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .plainClickable {
                            onStartActivity(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }

        item {
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = DimText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )
        }
    }
}

/** A titled, rounded card grouping related rows. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        color = DimText,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 8.dp, top = 28.dp, bottom = 10.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DimText, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun AppRow(label: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun ActionText(text: String, enabled: Boolean = true, dim: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.2f)
            dim -> DimText
            else -> Color.White
        },
        fontSize = 17.sp,
        modifier = Modifier
            .plainClickable { if (enabled) onClick() }
            .padding(vertical = 8.dp, horizontal = 2.dp),
    )
}
