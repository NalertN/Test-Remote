package de.lennart.minilauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "settings")

data class LauncherSettings(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<LauncherSettings> = dataStore.data.map { prefs ->
        LauncherSettings(
            favorites = prefs.favorites(),
            hidden = prefs[HIDDEN] ?: emptySet(),
            renames = prefs.renames(),
        )
    }

    suspend fun setFavorites(keys: List<String>) {
        dataStore.edit { it[FAVORITES] = keys.distinct().joinToString(SEPARATOR) }
    }

    suspend fun toggleFavorite(key: String) {
        dataStore.edit { prefs ->
            val current = prefs.favorites()
            val updated = when {
                key in current -> current - key
                current.size < MAX_FAVORITES -> current + key
                else -> current
            }
            prefs[FAVORITES] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun setHidden(key: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[HIDDEN] ?: emptySet()
            prefs[HIDDEN] = if (hidden) current + key else current - key
            if (hidden) prefs[FAVORITES] = (prefs.favorites() - key).joinToString(SEPARATOR)
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

    private fun Preferences.favorites(): List<String> =
        this[FAVORITES]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

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
        private val FAVORITES = stringPreferencesKey("favorites")
        private val HIDDEN = stringSetPreferencesKey("hidden")
        private val RENAMES = stringPreferencesKey("renames")
    }
}
