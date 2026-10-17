package com.armaldro.essence.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher")

/** Ordered list of favorite app keys, persisted across restarts. */
class FavoritesStore(private val context: Context) {
    private val key = stringPreferencesKey("favorites")

    val favorites: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[key]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun toggle(appKey: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
            val next = when {
                appKey in current -> current - appKey
                current.size >= MAX_FAVORITES -> current
                else -> current + appKey
            }
            prefs[key] = next.joinToString(SEPARATOR)
        }
    }

    companion object {
        const val MAX_FAVORITES = 8
        private const val SEPARATOR = "\n"
    }
}
