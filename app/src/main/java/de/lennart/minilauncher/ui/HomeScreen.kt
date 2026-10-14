package de.lennart.minilauncher.ui

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.lennart.minilauncher.data.AgendaDay
import de.lennart.minilauncher.data.AgendaEvent
import de.lennart.minilauncher.data.AppEntry
import kotlinx.coroutines.delay
import java.text.NumberFormat

@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    /** Shown below the clock when not null. */
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
            if (agenda != null) {
                Spacer(Modifier.height(28.dp))
                Agenda(agenda, onEventClick)
            }
            if (widgetId != null) {
                Spacer(Modifier.height(28.dp))
                WidgetSlot(widgetId, onLongPress = onWidgetLongPress)
            }
            // Favorites hang at a fixed distance below what's above them, so everything reads as one group.
            Spacer(Modifier.height(if (agenda == null && widgetId == null) 44.dp else 28.dp))
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

@Composable
private fun Clock(onClockClick: () -> Unit, onDateClick: (Long) -> Unit) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
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
    val is24Hour = DateFormat.is24HourFormat(context)
    val time = DateFormat.format(if (is24Hour) "HH:mm" else "h:mm", now).toString()
    val amPm = if (is24Hour) null else DateFormat.format("a", now).toString()
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), now).toString()

    val battery = rememberBatteryState()
    val percent = remember(battery.level, locale) {
        NumberFormat.getPercentInstance(locale).format(battery.level.toDouble())
    }

    Box(
        Modifier
            .size(240.dp)
            .plainClickable(onClick = onClockClick),
        contentAlignment = Alignment.Center,
    ) {
        BatteryRing(battery, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = buildAnnotatedString {
                    append(time)
                    if (amPm != null) {
                        withStyle(SpanStyle(fontSize = TypeScale.Caption, color = DimText)) { append(" $amPm") }
                    }
                },
                color = Color.White,
                fontSize = TypeScale.Time,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1.5).sp,
            )
            Text(
                text = percent,
                color = DimText,
                fontSize = TypeScale.Caption,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = date,
        color = DimText,
        fontSize = TypeScale.Caption,
        modifier = Modifier
            .plainClickable { onDateClick(now) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Thin ring whose bright arc shows the battery level; it gently pulses while charging. */
@Composable
private fun BatteryRing(battery: BatteryState, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(battery.level * 360f, tween(900), label = "battery")
    val pulse: State<Float>? = if (battery.charging) {
        rememberInfiniteTransition(label = "charging").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        null
    }
    val arcColor = if (battery.level <= 0.15f && !battery.charging) DangerColor else Color.White

    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke),
        )
        drawArc(
            color = arcColor.copy(alpha = pulse?.value ?: 1f),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
