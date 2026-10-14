package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", color = Color.White, fontSize = 32.sp)
                ActionText("Close", onClick = onClose)
            }
        }

        item { SectionHeader("Favorites") }
        if (favorites.isEmpty()) {
            item { Hint("Long-press an app to add it.") }
        }
        itemsIndexed(favorites, key = { _, app -> "fav-${app.key}" }) { index, app ->
            AppRow(app.label) {
                ActionText("↑", enabled = index > 0) { onMoveFavorite(app, -1) }
                ActionText("↓", enabled = index < favorites.lastIndex) { onMoveFavorite(app, 1) }
                ActionText("Remove") { onRemoveFavorite(app) }
            }
        }

        item { SectionHeader("Hidden apps") }
        if (hiddenApps.isEmpty()) {
            item { Hint("No hidden apps.") }
        }
        items(hiddenApps, key = { "hidden-${it.key}" }) { app ->
            AppRow(app.label) {
                ActionText("Unhide") { onUnhide(app) }
            }
        }

        item { SectionHeader("System") }
        item {
            Text(
                "Choose default launcher",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .plainClickable {
                        onStartActivity(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                    },
            )
        }
        item {
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = DimText,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = DimText, fontSize = 14.sp, modifier = Modifier.padding(top = 32.dp, bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DimText, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun AppRow(label: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun ActionText(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) Color.White else Color.DarkGray,
        fontSize = 18.sp,
        modifier = Modifier
            .plainClickable { if (enabled) onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
    )
}
