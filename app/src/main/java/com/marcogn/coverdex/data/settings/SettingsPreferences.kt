package com.marcogn.coverdex.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marcogn.coverdex.domain.model.ThemeMode
import com.marcogn.coverdex.domain.update.DEFAULT_UPDATE_FREQUENCY
import com.marcogn.coverdex.domain.update.UpdateFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Single DataStore file for every app-wide setting — one file, not one per preference
// (`docs/plan/phase-5-import-export-and-settings.md` §3). Internal, not private: tests need to
// write a raw, non-enum value to exercise the "unknown stored value falls back to SYSTEM" path.
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")
internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
internal val SHOW_MOVES_KEY = booleanPreferencesKey("show_moves")
internal val INCLUDE_MEGA_DYNAMAX_KEY = booleanPreferencesKey("include_mega_dynamax")
internal val EXCLUDE_LEGENDARIES_KEY = booleanPreferencesKey("exclude_legendaries")
internal val INCLUDE_CUSTOMS_ANALYSIS_KEY = booleanPreferencesKey("include_customs_analysis")
internal val SUGGESTION_COUNT_KEY = intPreferencesKey("suggestion_count")
internal val UPDATE_FREQUENCY_KEY = stringPreferencesKey("update_frequency")
internal val LAST_UPDATE_CHECK_KEY = longPreferencesKey("last_update_check_at")

internal const val MIN_SUGGESTION_COUNT = 5
internal const val MAX_SUGGESTION_COUNT = 10
internal const val DEFAULT_SUGGESTION_COUNT = MIN_SUGGESTION_COUNT

/**
 * Every app-wide setting, persisted with Preferences DataStore, each observable as a [Flow] —
 * theme, "Enable move slots" (Phase 0/3) plus the suggestion-pool filters this phase adds.
 * `showMoves`/`includeCustomsAnalysis`/`includeMegaDynamax`/`excludeLegendaries` are all global,
 * not per-team — `legacy-web`'s own `TeamDetailPage` receives every one of them as a single
 * app-wide prop, never team-scoped state. Named `SettingsPreferences`, not `ThemePreferences`
 * (its Phase 0-3 name): it now holds far more than the theme, and the plan's own phase-5 table
 * refers to it as `SettingsPreferences` throughout.
 */
@Singleton
class SettingsPreferences @Inject constructor(@ApplicationContext private val context: Context) {

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

    /** Whether Mega/Dynamax/Gigantamax forms are eligible suggestion candidates — filtered out of
     * the suggestion pool (never the species picker) when `false`, the default, matching
     * `AppSettings.includeMegaDynamax`'s own default in `useAppShell.ts`. */
    val includeMegaDynamax: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[INCLUDE_MEGA_DYNAMAX_KEY] ?: false
    }

    suspend fun setIncludeMegaDynamax(enabled: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[INCLUDE_MEGA_DYNAMAX_KEY] = enabled }
    }

    /** Stored inverted from how Settings presents it — the switch there reads "include
     * legendaries" (`AppSettings.excludeLegendaries`'s own row, `SettingsPage.tsx`'s
     * `!settings.excludeLegendaries`/`!e.target.checked`), so the stored name and the inversion
     * both carry over unchanged from the TypeScript. */
    val excludeLegendaries: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[EXCLUDE_LEGENDARIES_KEY] ?: false
    }

    suspend fun setExcludeLegendaries(excluded: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[EXCLUDE_LEGENDARIES_KEY] = excluded }
    }

    /** Whether the Analysis tab's suggestion pool includes the custom roster — global, unlike
     * the generation filter, which stays per-screen state (`AnalysisViewModel`). */
    val includeCustomsAnalysis: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[INCLUDE_CUSTOMS_ANALYSIS_KEY] ?: false
    }

    suspend fun setIncludeCustomsAnalysis(enabled: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[INCLUDE_CUSTOMS_ANALYSIS_KEY] = enabled }
    }

    /** How many ranked suggestions the Analysis tab's seventh section shows — Phase 7, see
     * docs/plan/phase-7-accuracy-and-customization.md §6. Clamped to
     * [MIN_SUGGESTION_COUNT]..[MAX_SUGGESTION_COUNT] on read too, not just on write: a
     * hand-edited or pre-Phase-7-incompatible store must degrade to a valid value, never crash or
     * render an unbounded list. */
    val suggestionCount: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        (preferences[SUGGESTION_COUNT_KEY] ?: DEFAULT_SUGGESTION_COUNT).coerceIn(MIN_SUGGESTION_COUNT, MAX_SUGGESTION_COUNT)
    }

    suspend fun setSuggestionCount(count: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SUGGESTION_COUNT_KEY] = count.coerceIn(MIN_SUGGESTION_COUNT, MAX_SUGGESTION_COUNT)
        }
    }

    /** How often the startup update check runs — Phase 8. Stored by enum name; an unknown name
     * (renamed constant, hand-edited store) falls back to the default. */
    val updateFrequency: Flow<UpdateFrequency> = context.settingsDataStore.data.map { preferences ->
        preferences[UPDATE_FREQUENCY_KEY]
            ?.let { name -> UpdateFrequency.entries.firstOrNull { it.name == name } }
            ?: DEFAULT_UPDATE_FREQUENCY
    }

    suspend fun setUpdateFrequency(frequency: UpdateFrequency) {
        context.settingsDataStore.edit { preferences -> preferences[UPDATE_FREQUENCY_KEY] = frequency.name }
    }

    /** Last check that got an answer from GitHub (update or not); `null` = never. Stored as epoch millis. */
    val lastUpdateCheckAt: Flow<Instant?> = context.settingsDataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK_KEY]?.takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
    }

    suspend fun setLastUpdateCheckAt(at: Instant) {
        context.settingsDataStore.edit { preferences -> preferences[LAST_UPDATE_CHECK_KEY] = at.toEpochMilli() }
    }

    suspend fun currentUpdateFrequency(): UpdateFrequency = updateFrequency.first()
    suspend fun currentLastUpdateCheckAt(): Instant? = lastUpdateCheckAt.first()
}
