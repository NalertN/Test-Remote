package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.lennart.minilauncher.data.AppEntry
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalLocale

@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartActivity: (Intent, Intent?) -> Unit,
) {
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -swipeThreshold) onOpenDrawer() },
                    onVerticalDrag = { _, dragAmount -> total += dragAmount },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onOpenSettings() })
            },
    ) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            Clock(
                onClockClick = {
                    onStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), null)
                },
                onDateClick = { now ->
                    onStartActivity(
                        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
                        Intent(
                            Intent.ACTION_VIEW,
                            CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                                .appendPath(now.toString()).build(),
                        ),
                    )
                },
            )
            Spacer(Modifier.height(56.dp))
            favorites.forEach { app ->
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .plainClickable(onLongClick = { onAppLongPress(app) }) { onLaunch(app) },
                )
            }
        }
    }
}

@Composable
private fun Clock(onClockClick: () -> Unit, onDateClick: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Ticks once per minute while visible; resyncs immediately on resume (delay() pauses in deep sleep).
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000 - value % 60_000 + 50)
            }
        }
    }
    val time = DateFormat.getTimeFormat(context).format(now)
    val datePattern = DateFormat.getBestDateTimePattern(LocalLocale.current.platformLocale, "EEEEdMMMM")
    val date = DateFormat.format(datePattern, now).toString()

    Text(
        text = time,
        color = Color.White,
        fontSize = 72.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.plainClickable(onClick = onClockClick),
    )
    Text(
        text = date,
        color = DimText,
        fontSize = 20.sp,
        modifier = Modifier.plainClickable { onDateClick(now) },
    )
}
