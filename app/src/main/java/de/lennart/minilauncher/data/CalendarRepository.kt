package de.lennart.minilauncher.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class AgendaEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    /** First and last day (inclusive) the event covers, in the device's time zone. */
    val firstDay: LocalDate,
    val lastDay: LocalDate,
)

data class AgendaDay(val date: LocalDate, val events: List<AgendaEvent>)

/** Upcoming event instances from all visible calendars, read via [CalendarContract.Instances]. */
class CalendarRepository(context: Context) {
    private val context = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Events of the next [days] days (today included), grouped by day. Events that started
     * earlier but are still running are listed under today. Returns null without permission.
     */
    suspend fun load(days: Long = 7): List<AgendaDay>? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val endExclusive = today.plusDays(days)
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, today.atStartOfDay(zone).toInstant().toEpochMilli())
            ContentUris.appendId(it, endExclusive.atStartOfDay(zone).toInstant().toEpochMilli())
        }.build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY)
        val selection = "${Instances.VISIBLE} = 1 AND " +
            "${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}"

        val events = mutableListOf<AgendaEvent>()
        try {
            context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val begin = c.getLong(2)
                    val end = c.getLong(3)
                    val allDay = c.getInt(4) != 0
                    // All-day events are stored as UTC midnights; their end is exclusive.
                    val dayZone = if (allDay) ZoneOffset.UTC else zone
                    val firstDay = Instant.ofEpochMilli(begin).atZone(dayZone).toLocalDate()
                    val lastDay = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(dayZone).toLocalDate()
                    if (lastDay < today || firstDay >= endExclusive) continue
                    events += AgendaEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.trim().orEmpty().ifEmpty { "(No title)" },
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        firstDay = firstDay,
                        lastDay = lastDay,
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext null
        }

        events
            .groupBy { maxOf(it.firstDay, today) }
            .toSortedMap()
            .map { (day, list) -> AgendaDay(day, list.sortedWith(compareBy({ !it.allDay }, { it.begin }))) }
    }

    /** Calls [onChange] when calendar data, the date or the time zone changes. Returns an unregister function. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange()
        }
        val observing = runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return {
            if (observing) context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    fun eventIntent(event: AgendaEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
}
