package de.lennart.minilauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import de.lennart.minilauncher.LauncherApplication
import de.lennart.minilauncher.WidgetOption
import de.lennart.minilauncher.widget.LongPressWidgetHostView

/** Hosts the bound app widget; long-pressing it calls [onLongPress]. */
@Composable
fun WidgetSlot(widgetId: Int, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val host = (context.applicationContext as LauncherApplication).widgetHost
    val info = remember(widgetId) { AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) }
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    if (info == null) {
        // The widget's app was uninstalled or disabled.
        Text(
            "Widget unavailable · long-press to change",
            color = DimText,
            fontSize = TypeScale.Caption,
            textAlign = TextAlign.Center,
            modifier = modifier
                .plainClickable(onLongClick = onLongPress) {}
                .padding(16.dp),
        )
        return
    }

    key(widgetId) {
        AndroidView(
            factory = { viewContext ->
                (host.createView(viewContext, widgetId, info) as LongPressWidgetHostView).apply {
                    this.onLongPress = { currentOnLongPress() }
                    contentDescription = "Home widget"
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .height(WidgetHeight)
                .clip(RoundedCornerShape(24.dp)),
        )
    }
}

private val WidgetHeight = 320.dp

/** Text-only list of all installed widgets, by app. */
@Composable
fun WidgetPickerDialog(options: List<WidgetOption>, onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SurfaceColor, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Choose widget", color = DimText, fontSize = TypeScale.Caption, modifier = Modifier.padding(bottom = 12.dp))
                if (options.isEmpty()) {
                    Text("No widgets installed", color = Color.White, fontSize = TypeScale.Body, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(options, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { option ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .plainClickable { onPick(option.info) }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(option.label, color = Color.White, fontSize = TypeScale.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (option.label != option.appLabel) {
                                Text(option.appLabel, color = DimText, fontSize = TypeScale.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetActionsDialog(onReplace: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    ActionsDialog(
        title = "Widget",
        actions = listOf(
            DialogAction("Replace widget", onClick = onReplace),
            DialogAction("Remove widget", danger = true, onClick = onRemove),
        ),
        onDismiss = onDismiss,
    )
}
