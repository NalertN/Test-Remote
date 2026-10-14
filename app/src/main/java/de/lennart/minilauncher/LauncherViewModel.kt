package de.lennart.minilauncher

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.lennart.minilauncher.data.AgendaDay
import de.lennart.minilauncher.data.AgendaEvent
import de.lennart.minilauncher.data.AppEntry
import de.lennart.minilauncher.data.CalendarRepository
import de.lennart.minilauncher.data.LauncherSettings
import de.lennart.minilauncher.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

enum class Screen { Home, Drawer, Agenda, Settings }

/** An installable widget, labelled for the picker. */
data class WidgetOption(val info: AppWidgetProviderInfo, val appLabel: String, val label: String)

data class LauncherState(
    val favorites: List<AppEntry> = emptyList(),
    /** Visible (non-hidden) apps, sorted alphabetically by display label. */
    val apps: List<AppEntry> = emptyList(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val widgetId: Int? = null,
    val agendaOnOwnPage: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val launcherApp = application as LauncherApplication
    private val appRepository = launcherApp.appRepository
    private val settingsRepository = launcherApp.settingsRepository
    private val calendarRepository = CalendarRepository(application)
    private val widgetHost = launcherApp.widgetHost

    var screen by mutableStateOf(Screen.Home)
        private set
    var query by mutableStateOf("")
    var menuApp by mutableStateOf<AppEntry?>(null)
    var renameApp by mutableStateOf<AppEntry?>(null)
    var crashReport by mutableStateOf<String?>(null)
        private set

    /** Upcoming events grouped by day; null hides the agenda (no calendar permission). */
    var agenda by mutableStateOf<List<AgendaDay>?>(null)
        private set
    private var agendaJob: Job? = null
    private var stopWatchingCalendar: (() -> Unit)? = null

    /** Installed widgets while the picker is open, null otherwise. */
    var widgetOptions by mutableStateOf<List<WidgetOption>?>(null)
        private set
    var widgetMenuOpen by mutableStateOf(false)

    val state: StateFlow<LauncherState> =
        combine(appRepository.apps, settingsRepository.settings, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())

    init {
        crashReport = CrashHandler.read(application)
    }

    private fun buildState(installed: List<AppEntry>, settings: LauncherSettings): LauncherState {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val labeled = installed
            .map { app -> settings.renames[app.key]?.let { app.copy(label = it) } ?: app }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        val byKey = labeled.associateBy { it.key }
        return LauncherState(
            favorites = settings.favorites.mapNotNull { byKey[it] },
            apps = labeled.filter { it.key !in settings.hidden },
            hiddenApps = labeled.filter { it.key in settings.hidden },
            favoriteKeys = settings.favorites.toSet(),
            widgetId = settings.widgetId,
            agendaOnOwnPage = settings.agendaOnOwnPage,
        )
    }

    /** Apps matching [query]; prefix matches first. Empty query returns all apps. */
    fun filteredApps(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = query.trim()
        if (q.isEmpty()) return apps
        val matches = apps.filter { it.label.contains(q, ignoreCase = true) }
        val (prefix, rest) = matches.partition { app ->
            app.label.startsWith(q, ignoreCase = true) ||
                app.label.split(' ').any { it.startsWith(q, ignoreCase = true) }
        }
        return prefix + rest
    }

    fun openDrawer() {
        query = ""
        screen = Screen.Drawer
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun openAgenda() {
        screen = Screen.Agenda
    }

    fun goHome() {
        screen = Screen.Home
        query = ""
        menuApp = null
        renameApp = null
        widgetOptions = null
        widgetMenuOpen = false
    }

    fun refreshApps() = appRepository.refresh()

    fun launch(app: AppEntry) {
        runCatching { appRepository.launch(app) }.onFailure { toast("Cannot open ${app.label}") }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { appRepository.openAppInfo(app) }.onFailure { toast("Cannot open app info") }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun startActivity(intent: Intent, fallback: Intent? = null) {
        val context = getApplication<Application>()
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) startActivity(fallback) else toast("No app found")
        } catch (e: SecurityException) {
            toast("Not allowed")
        }
    }

    fun toggleFavorite(app: AppEntry) {
        if (app.key !in state.value.favoriteKeys && state.value.favorites.size >= SettingsRepository.MAX_FAVORITES) {
            toast("Only ${SettingsRepository.MAX_FAVORITES} favorites allowed")
            return
        }
        viewModelScope.launch { settingsRepository.toggleFavorite(app.key) }
    }

    fun moveFavorite(app: AppEntry, offset: Int) {
        val keys = state.value.favorites.map { it.key }.toMutableList()
        val from = keys.indexOf(app.key)
        val to = from + offset
        if (from < 0 || to !in keys.indices) return
        keys.add(to, keys.removeAt(from))
        viewModelScope.launch { settingsRepository.setFavorites(keys) }
    }

    fun setHidden(app: AppEntry, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setHidden(app.key, hidden) }
    }

    fun rename(app: AppEntry, name: String) {
        viewModelScope.launch { settingsRepository.rename(app.key, name) }
    }

    // Calendar

    fun hasCalendarPermission() = calendarRepository.hasPermission()

    /** Reloads the agenda; [debounce] coalesces bursts of change notifications during sync. */
    fun refreshAgenda(debounce: Boolean = false) {
        agendaJob?.cancel()
        if (!calendarRepository.hasPermission()) {
            agenda = null
            return
        }
        agendaJob = viewModelScope.launch {
            if (debounce) delay(500)
            agenda = calendarRepository.load()
        }
    }

    /** Keeps the agenda current while the launcher is visible. */
    fun startWatchingCalendar() {
        refreshAgenda()
        if (stopWatchingCalendar == null && calendarRepository.hasPermission()) {
            stopWatchingCalendar = calendarRepository.observe { refreshAgenda(debounce = true) }
        }
    }

    fun stopWatchingCalendar() {
        stopWatchingCalendar?.invoke()
        stopWatchingCalendar = null
    }

    fun openEvent(event: AgendaEvent) = startActivity(calendarRepository.eventIntent(event))

    fun openCalendarPermissionSettings() {
        val context = getApplication<Application>()
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    fun setAgendaOnOwnPage(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAgendaOnOwnPage(enabled) }
    }

    // Widget

    fun openWidgetPicker() {
        widgetMenuOpen = false
        viewModelScope.launch {
            val context = getApplication<Application>()
            val pm = context.packageManager
            widgetOptions = withContext(Dispatchers.IO) {
                val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
                AppWidgetManager.getInstance(context).installedProviders
                    .map { info ->
                        val appLabel = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                        }.getOrElse { info.provider.packageName }
                        WidgetOption(info, appLabel, info.loadLabel(pm).orEmpty().ifEmpty { appLabel })
                    }
                    .sortedWith { a, b ->
                        collator.compare(a.appLabel, b.appLabel).takeIf { it != 0 } ?: collator.compare(a.label, b.label)
                    }
            }
        }
    }

    fun closeWidgetPicker() {
        widgetOptions = null
    }

    /** Shows [id] in the widget slot and releases the widget it replaces. */
    fun setWidget(id: Int) {
        viewModelScope.launch {
            val previous = settingsRepository.setWidgetId(id)
            if (previous != null && previous != id) widgetHost.deleteAppWidgetId(previous)
        }
    }

    fun removeWidget() {
        widgetMenuOpen = false
        viewModelScope.launch {
            settingsRepository.setWidgetId(null)?.let(widgetHost::deleteAppWidgetId)
        }
    }

    /** Releases widget IDs left over from interrupted or crashed bind attempts. */
    fun releaseOrphanedWidgets() {
        viewModelScope.launch {
            val keep = settingsRepository.settings.first().widgetId
            widgetHost.appWidgetIds.filter { it != keep }.forEach(widgetHost::deleteAppWidgetId)
        }
    }

    fun dismissCrashReport() {
        CrashHandler.clear(getApplication())
        crashReport = null
    }

    override fun onCleared() {
        stopWatchingCalendar()
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}
