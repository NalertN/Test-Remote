package de.lennart.minilauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.lennart.minilauncher.data.AgendaDay
import de.lennart.minilauncher.data.AgendaEvent
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Plain text list of upcoming events, grouped under day headings. */
@Composable
fun Agenda(days: List<AgendaDay>, onEventClick: (AgendaEvent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val is24Hour = DateFormat.is24HourFormat(context)
    val today = LocalDate.now()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (days.isEmpty()) {
            Text("No events in the next 7 days", color = DimText, fontSize = TypeScale.Caption)
            return@Column
        }
        days.forEachIndexed { index, day ->
            Text(
                text = dayLabel(day.date, today, locale),
                color = DimText,
                fontSize = TypeScale.Small,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 16.dp, bottom = 2.dp),
            )
            day.events.forEach { event ->
                Text(
                    text = buildAnnotatedString {
                        eventPrefix(event, day.date, is24Hour, locale)?.let {
                            withStyle(SpanStyle(color = DimText)) { append(it) }
                            append("  ")
                        }
                        append(event.title)
                    },
                    color = Color.White,
                    fontSize = TypeScale.Caption,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .plainClickable { onEventClick(event) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** The agenda on its own page, reached by swiping left on home; swiping right goes back. */
@Composable
fun AgendaScreen(days: List<AgendaDay>, onEventClick: (AgendaEvent) -> Unit, onClose: () -> Unit) {
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(onClose) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > swipeThreshold) onClose() },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                )
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Next 7 days", color = Color.White, fontSize = TypeScale.Title)
        Spacer(Modifier.height(28.dp))
        Agenda(days, onEventClick, Modifier.fillMaxWidth())
        Spacer(Modifier.height(40.dp))
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> DateFormat.format(
        DateFormat.getBestDateTimePattern(locale, "EEEEdMMM"),
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    ).toString()
}

/** Start time for timed events; for events spanning several days, when they end. */
private fun eventPrefix(event: AgendaEvent, shownOn: LocalDate, is24Hour: Boolean, locale: Locale): String? {
    if (event.lastDay > shownOn) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        val endDay = event.lastDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return "until ${DateFormat.format(pattern, endDay)}"
    }
    if (event.allDay || event.firstDay < shownOn) return null
    return DateFormat.format(if (is24Hour) "HH:mm" else "h:mm a", event.begin).toString()
}
