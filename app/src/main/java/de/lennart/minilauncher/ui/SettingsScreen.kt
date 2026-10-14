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
    favorites: List<AppEntry>,
    hiddenApps: List<AppEntry>,
    hasWidget: Boolean,
    onChooseWidget: () -> Unit,
    onRemoveWidget: () -> Unit,
    calendarAllowed: Boolean,
    onAllowCalendar: () -> Unit,
    agendaOnOwnPage: Boolean,
    onAgendaOnOwnPageChange: (Boolean) -> Unit,
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

        item { SectionTitle("Favorites") }
        if (favorites.isEmpty()) {
            item { Hint("Long-press an app to add it.") }
        }
        itemsIndexed(favorites, key = { _, app -> "fav-${app.key}" }) { index, app ->
            AppRow(app.label) {
                ActionText("↑", enabled = index > 0) { onMoveFavorite(app, -1) }
                ActionText("↓", enabled = index < favorites.lastIndex) { onMoveFavorite(app, 1) }
                ActionText("Remove", dim = true) { onRemoveFavorite(app) }
            }
        }

        item { SectionTitle("Hidden apps") }
        if (hiddenApps.isEmpty()) {
            item { Hint("Hidden apps don't appear in the app list.") }
        }
        items(hiddenApps, key = { "hidden-${it.key}" }) { app ->
            AppRow(app.label) {
                ActionText("Unhide", dim = true) { onUnhide(app) }
            }
        }

        item { SectionTitle("Widget") }
        item {
            AppRow(if (hasWidget) "Home widget" else "No widget") {
                ActionText(if (hasWidget) "Replace" else "Add", onClick = onChooseWidget)
                if (hasWidget) ActionText("Remove", dim = true, onClick = onRemoveWidget)
            }
        }
        item { Hint("Shown below the agenda. Long-press it to replace or remove.") }

        item { SectionTitle("Calendar") }
        if (calendarAllowed) {
            item {
                AppRow(if (agendaOnOwnPage) "Agenda on its own page" else "Agenda on the home screen") {
                    ActionText("Change", dim = true) { onAgendaOnOwnPageChange(!agendaOnOwnPage) }
                }
            }
            if (agendaOnOwnPage) item { Hint("Swipe left on the home screen to see it.") }
        } else {
            item { SettingLink("Allow calendar access", onAllowCalendar) }
            item { Hint("Under Permissions → Calendar. The agenda stays hidden until then.") }
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
