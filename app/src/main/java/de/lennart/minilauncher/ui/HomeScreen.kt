package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.lennart.minilauncher.data.AgendaDay
import de.lennart.minilauncher.data.AgendaEvent
import de.lennart.minilauncher.data.AppEntry
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    /** Shown below the date when not null. */
    agenda: List<AgendaDay>?,
    onEventClick: (AgendaEvent) -> Unit,
    widgetId: Int?,
    onWidgetLongPress: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    /** Opens the agenda page on swipe left; null when the agenda isn't on its own page. */
    onOpenAgenda: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onStartActivity: (Intent, Intent?) -> Unit,
) {
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(onOpenAgenda == null) {
                // Swiping right opens the app list, swiping left the agenda page.
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > swipeThreshold) onOpenDrawer()
                        if (total < -swipeThreshold) onOpenAgenda?.invoke()
                    },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onOpenSettings() })
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            DateHeader { now ->
                onStartActivity(
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
                    Intent(
                        Intent.ACTION_VIEW,
                        CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                            .appendPath(now.toString()).build(),
                    ),
                )
            }
            if (agenda != null) {
                Spacer(Modifier.height(28.dp))
                Agenda(agenda, onEventClick)
            }
            if (widgetId != null) {
                Spacer(Modifier.height(28.dp))
                WidgetSlot(widgetId, onLongPress = onWidgetLongPress)
            }
            // Favorites hang at a fixed distance below what's above them, so everything reads as one group.
            Spacer(Modifier.height(28.dp))
            favorites.forEach { app ->
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = TypeScale.Title,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .plainClickable(onLongClick = { onAppLongPress(app) }) { onLaunch(app) }
                        .padding(horizontal = 24.dp, vertical = 11.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Today's date as the screen's heading; time and battery are already in the status bar. */
@Composable
private fun DateHeader(onClick: (Long) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Ticks once per minute while visible so the date flips at midnight; resyncs on resume.
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000 - value % 60_000 + 50)
            }
        }
    }
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), now).toString()
    Text(
        text = date,
        color = Color.White,
        fontSize = TypeScale.Title,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .plainClickable { onClick(now) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
