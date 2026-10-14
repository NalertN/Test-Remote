package de.lennart.minilauncher

import android.app.Application
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
import de.lennart.minilauncher.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

enum class Screen { Home, Drawer, Settings }

data class LauncherState(
    val favorites: List<AppEntry> = emptyList(),
    /** Visible (non-hidden) apps, sorted alphabetically by display label. */
    val apps: List<AppEntry> = emptyList(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val launcherApp = application as LauncherApplication
    private val appRepository = launcherApp.appRepository
    private val settingsRepository = launcherApp.settingsRepository

    var screen by mutableStateOf(Screen.Home)
        private set
    var query by mutableStateOf("")
    var menuApp by mutableStateOf<AppEntry?>(null)
    var renameApp by mutableStateOf<AppEntry?>(null)
    var crashReport by mutableStateOf<String?>(null)
        private set

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

    fun goHome() {
        screen = Screen.Home
        query = ""
        menuApp = null
        renameApp = null
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

    fun dismissCrashReport() {
        CrashHandler.clear(getApplication())
        crashReport = null
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}
