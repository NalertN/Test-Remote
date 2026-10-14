package de.lennart.minilauncher

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.lennart.minilauncher.data.AppEntry
import de.lennart.minilauncher.data.LauncherSettings
import de.lennart.minilauncher.data.PAGES
import de.lennart.minilauncher.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** Home page, second page (swipe right), app list (swipe up) and settings. */
enum class Screen { Home, Second, Drawer, Settings }

/** An installable widget, labelled for the picker. */
data class WidgetOption(val info: AppWidgetProviderInfo, val appLabel: String, val label: String)

data class LauncherState(
    /** Favorites of the home page (index 0) and the second page (index 1). */
    val favorites: List<List<AppEntry>> = List(PAGES) { emptyList() },
    /** Visible (non-hidden) apps, sorted alphabetically by display label. */
    val apps: List<AppEntry> = emptyList(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val favoriteKeys: List<Set<String>> = List(PAGES) { emptySet() },
    /** Widget above the favorites of each page, if any. */
    val widgetIds: List<Int?> = List(PAGES) { null },
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val launcherApp = application as LauncherApplication
    private val appRepository = launcherApp.appRepository
    private val settingsRepository = launcherApp.settingsRepository
    private val widgetHost = launcherApp.widgetHost

    var screen by mutableStateOf(Screen.Home)
        private set
    var query by mutableStateOf("")
    var menuApp by mutableStateOf<AppEntry?>(null)
    var renameApp by mutableStateOf<AppEntry?>(null)
    var crashReport by mutableStateOf<String?>(null)
        private set

    /** Page whose widget picker is open, and the installed widgets to pick from (null while loading). */
    var widgetPickerPage by mutableStateOf<Int?>(null)
        private set
    var widgetOptions by mutableStateOf<List<WidgetOption>?>(null)
        private set

    /** Page whose widget menu (replace / remove) is open. */
    var widgetMenuPage by mutableStateOf<Int?>(null)

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
            favorites = settings.favorites.map { keys -> keys.mapNotNull { byKey[it] } },
            apps = labeled.filter { it.key !in settings.hidden },
            hiddenApps = labeled.filter { it.key in settings.hidden },
            favoriteKeys = settings.favorites.map { it.toSet() },
            widgetIds = settings.widgetIds,
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

    fun openSecondPage() {
        screen = Screen.Second
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun goHome() {
        screen = Screen.Home
        query = ""
        menuApp = null
        renameApp = null
        widgetPickerPage = null
        widgetOptions = null
        widgetMenuPage = null
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

    fun toggleFavorite(page: Int, app: AppEntry) {
        val favorites = state.value.favorites[page]
        if (app.key !in state.value.favoriteKeys[page] && favorites.size >= SettingsRepository.MAX_FAVORITES) {
            toast("Only ${SettingsRepository.MAX_FAVORITES} favorites per page")
            return
        }
        viewModelScope.launch { settingsRepository.toggleFavorite(page, app.key) }
    }

    fun moveFavorite(page: Int, app: AppEntry, offset: Int) {
        val keys = state.value.favorites[page].map { it.key }.toMutableList()
        val from = keys.indexOf(app.key)
        val to = from + offset
        if (from < 0 || to !in keys.indices) return
        keys.add(to, keys.removeAt(from))
        viewModelScope.launch { settingsRepository.setFavorites(page, keys) }
    }

    fun setHidden(app: AppEntry, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setHidden(app.key, hidden) }
    }

    fun rename(app: AppEntry, name: String) {
        viewModelScope.launch { settingsRepository.rename(app.key, name) }
    }

    // Widgets

    /** Opens the list of installed widgets to put above the favorites of [page]. */
    fun openWidgetPicker(page: Int) {
        widgetMenuPage = null
        widgetPickerPage = page
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
        widgetPickerPage = null
        widgetOptions = null
    }

    /** Shows [id] above the favorites of [page] and releases the widget it replaces. */
    fun setWidget(page: Int, id: Int) {
        viewModelScope.launch {
            val previous = settingsRepository.setWidgetId(page, id)
            if (previous != null && previous != id) widgetHost.deleteAppWidgetId(previous)
        }
    }

    fun removeWidget(page: Int) {
        widgetMenuPage = null
        viewModelScope.launch {
            settingsRepository.setWidgetId(page, null)?.let(widgetHost::deleteAppWidgetId)
        }
    }

    /** Releases widget IDs left over from interrupted or crashed bind attempts. */
    fun releaseOrphanedWidgets() {
        viewModelScope.launch {
            val keep = settingsRepository.settings.first().widgetIds.filterNotNull().toSet()
            widgetHost.appWidgetIds.filter { it !in keep }.forEach(widgetHost::deleteAppWidgetId)
        }
    }

    fun dismissCrashReport() {
        CrashHandler.clear(getApplication())
        crashReport = null
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}
