package com.marcogn.coverdex.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marcogn.coverdex.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore file for every app-wide setting (theme, language-adjacent prefs, and later
// phases' include-Mega/Dynamax, exclude-legendaries, show-moves, etc.) — one file, not one per
// preference. Internal, not private: tests need to write a raw, non-enum value to exercise the
// "unknown stored value falls back to SYSTEM" path.
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")
internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
internal val SHOW_MOVES_KEY = booleanPreferencesKey("show_moves")

/** Theme and "Enable move slots" preferences, persisted with Preferences DataStore, each
 * observable as a [Flow]. `showMoves` is global, not per-team — `legacy-web`'s own
 * `TeamDetailPage` receives it as a single app-wide prop, not team-scoped state. */
@Singleton
class ThemePreferences @Inject constructor(@ApplicationContext private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
    }

    val showMoves: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SHOW_MOVES_KEY] ?: false
    }

    suspend fun setShowMoves(enabled: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[SHOW_MOVES_KEY] = enabled }
    }
}
