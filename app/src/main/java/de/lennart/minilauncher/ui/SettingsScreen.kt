package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.lennart.minilauncher.BuildConfig
import de.lennart.minilauncher.data.AppEntry

@Composable
fun SettingsScreen(
    /** Favorites of the home page (index 0) and the second page (index 1). */
    favorites: List<List<AppEntry>>,
    hiddenApps: List<AppEntry>,
    widgetIds: List<Int?>,
    onChooseWidget: (page: Int) -> Unit,
    onRemoveWidget: (page: Int) -> Unit,
    onMoveFavorite: (page: Int, AppEntry, Int) -> Unit,
    onRemoveFavorite: (page: Int, AppEntry) -> Unit,
    onUnhide: (AppEntry) -> Unit,
    onStartActivity: (Intent, Intent?) -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 40.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", color = Color.White, fontSize = TypeScale.Title, fontWeight = FontWeight.Medium)
                ActionText("Done", onClick = onClose)
            }
        }

        for (page in favorites.indices) {
            item { SectionTitle(if (page == 0) "Favorites 1 · home" else "Favorites 2 · swipe right") }
            if (favorites[page].isEmpty()) {
                item { Hint("Long-press an app in the app list to add it.") }
            }
            itemsIndexed(favorites[page], key = { _, app -> "fav$page-${app.key}" }) { index, app ->
                AppRow(app.label) {
                    ActionText("↑", enabled = index > 0) { onMoveFavorite(page, app, -1) }
                    ActionText("↓", enabled = index < favorites[page].lastIndex) { onMoveFavorite(page, app, 1) }
                    ActionText("Remove", dim = true) { onRemoveFavorite(page, app) }
                }
            }
        }

        item { SectionTitle("Widgets") }
        for (page in widgetIds.indices) {
            item {
                val hasWidget = widgetIds[page] != null
                AppRow(if (page == 0) "Home widget" else "Second page widget") {
                    ActionText(if (hasWidget) "Replace" else "Add") { onChooseWidget(page) }
                    if (hasWidget) ActionText("Remove", dim = true) { onRemoveWidget(page) }
                }
            }
        }
        item { Hint("A widget sits above the favorites of its page. Long-press it to replace or remove it.") }

        item { SectionTitle("Hidden apps") }
        if (hiddenApps.isEmpty()) {
            item { Hint("Hidden apps don't appear in the app list.") }
        }
        items(hiddenApps, key = { "hidden-${it.key}" }) { app ->
            AppRow(app.label) {
                ActionText("Unhide", dim = true) { onUnhide(app) }
            }
        }

        item { SectionTitle("Launcher") }
        item {
            SettingLink("Choose default launcher") {
                onStartActivity(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            }
        }
        item {
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = DimText,
                fontSize = TypeScale.Small,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = TypeScale.Body,
        modifier = Modifier
            .fillMaxWidth()
            .plainClickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = DimText, fontSize = TypeScale.Caption, modifier = Modifier.padding(top = 36.dp, bottom = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DimText, fontSize = TypeScale.Caption, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun AppRow(label: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = TypeScale.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Text button with at least a 48dp touch target. */
@Composable
private fun ActionText(text: String, enabled: Boolean = true, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .plainClickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.2f)
                dim -> DimText
                else -> Color.White
            },
            fontSize = TypeScale.Caption,
        )
    }
}
