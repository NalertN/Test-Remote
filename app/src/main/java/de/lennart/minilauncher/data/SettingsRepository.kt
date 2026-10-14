package de.lennart.minilauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "settings")

data class LauncherSettings(
    /** Favorite app keys of the home page (index 0) and the second page (index 1). */
    val favorites: List<List<String>> = List(PAGES) { emptyList() },
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    /** App widget IDs above the favorites of each page; null for an empty slot. */
    val widgetIds: List<Int?> = List(PAGES) { null },
)

/** Home page and second page. */
const val PAGES = 2

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<LauncherSettings> = dataStore.data.map { prefs ->
        LauncherSettings(
            favorites = List(PAGES) { prefs.favorites(it) },
            hidden = prefs[HIDDEN] ?: emptySet(),
            renames = prefs.renames(),
            widgetIds = List(PAGES) { prefs[WIDGET_IDS[it]] },
        )
    }

    /** Replaces the favorites of [page] (0 = home, 1 = second page). */
    suspend fun setFavorites(page: Int, keys: List<String>) {
        dataStore.edit { it[FAVORITES[page]] = keys.distinct().joinToString(SEPARATOR) }
    }

    suspend fun toggleFavorite(page: Int, key: String) {
        dataStore.edit { prefs ->
            val current = prefs.favorites(page)
            val updated = when {
                key in current -> current - key
                current.size < MAX_FAVORITES -> current + key
                else -> current
            }
            prefs[FAVORITES[page]] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun setHidden(key: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[HIDDEN] ?: emptySet()
            prefs[HIDDEN] = if (hidden) current + key else current - key
            if (hidden) {
                for (page in 0 until PAGES) {
                    prefs[FAVORITES[page]] = (prefs.favorites(page) - key).joinToString(SEPARATOR)
                }
            }
        }
    }

    /** A blank [name] removes the custom label. */
    suspend fun rename(key: String, name: String) {
        dataStore.edit { prefs ->
            val renames = prefs.renames().toMutableMap()
            if (name.isBlank()) renames.remove(key) else renames[key] = name.trim()
            prefs[RENAMES] = JSONObject(renames as Map<*, *>).toString()
        }
    }

    /** Stores the widget of [page] ([id] null removes it) and returns the previous one. */
    suspend fun setWidgetId(page: Int, id: Int?): Int? {
        var previous: Int? = null
        dataStore.edit { prefs ->
            previous = prefs[WIDGET_IDS[page]]
            if (id == null) prefs.remove(WIDGET_IDS[page]) else prefs[WIDGET_IDS[page]] = id
        }
        return previous
    }

    private fun Preferences.favorites(page: Int): List<String> =
        this[FAVORITES[page]]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    private fun Preferences.renames(): Map<String, String> {
        val json = this[RENAMES] ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val MAX_FAVORITES = 8
        private const val SEPARATOR = "\n"
        // The first entries keep the old key names, so existing favorites and widget stay.
        private val FAVORITES = arrayOf(stringPreferencesKey("favorites"), stringPreferencesKey("favorites2"))
        private val HIDDEN = stringSetPreferencesKey("hidden")
        private val RENAMES = stringPreferencesKey("renames")
        private val WIDGET_IDS = arrayOf(intPreferencesKey("widget_id"), intPreferencesKey("widget_id_2"))
    }
}
