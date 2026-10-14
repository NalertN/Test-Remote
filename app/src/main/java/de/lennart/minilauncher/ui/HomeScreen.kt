package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
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
import de.lennart.minilauncher.Swipe
import de.lennart.minilauncher.classifySwipe
import de.lennart.minilauncher.data.AppEntry
import kotlinx.coroutines.delay

/**
 * One of the two home pages: a widget on top, the page's favorites at the bottom within thumb
 * reach. [page] 0 is the home page (with today's date), page 1 the second page.
 *
 * Swipe up opens the app list; [onSwipeRight] / [onSwipeLeft] move between the pages and are
 * null where there is no page in that direction. Long-pressing empty space opens settings.
 */
@Composable
fun FavoritesPage(
    page: Int,
    favorites: List<AppEntry>,
    widgetId: Int?,
    onLaunch: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    /** Long-press on the widget, or on the hint where there is none. */
    onWidgetLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeRight: (() -> Unit)?,
    onSwipeLeft: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onStartActivity: (Intent, Intent?) -> Unit,
) {
    val swipeDistance = with(LocalDensity.current) { 80.dp.toPx() }
    val currentSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val swipes = buildSet {
        add(Swipe.Up)
        if (onSwipeRight != null) add(Swipe.Right)
        if (onSwipeLeft != null) add(Swipe.Left)
    }
    val onSwipe: (Swipe) -> Unit = {
        when (it) {
            Swipe.Up -> currentSwipeUp()
            Swipe.Right -> currentSwipeRight?.invoke()
            Swipe.Left -> currentSwipeLeft?.invoke()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(swipes) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = { classifySwipe(dx, dy, swipeDistance)?.takeIf { it in swipes }?.let(onSwipe) },
                    onDrag = { _, amount -> dx += amount.x; dy += amount.y },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            if (page == 0) {
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
                Spacer(Modifier.height(12.dp))
            }
            WidgetSlot(
                page = page,
                widgetId = widgetId,
                swipes = swipes,
                onSwipe = onSwipe,
                onLongPress = onWidgetLongPress,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(16.dp))
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Today's date as the home page's heading; time and battery are already in the status bar. */
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
