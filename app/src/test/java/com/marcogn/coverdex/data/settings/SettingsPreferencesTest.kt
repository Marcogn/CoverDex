package com.marcogn.coverdex.data.settings

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26 (Hall of Memories' minSdk, kept here too): Robolectric's shadow jar for this app's
// compileSdk (36) requires a newer JDK than CI runs (see CLAUDE.md, "Known gotchas") — pinning
// sidesteps that without weakening what's tested. CoverDex's own minSdk stays 24 (see
// docs/plan/native-spec.md); this pin is only about Robolectric's JDK requirement, not a claim
// about the app's supported API range.
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class SettingsPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val preferences = SettingsPreferences(context)

    // Robolectric reuses the same on-disk filesDir across test methods within a run, and
    // preferencesDataStore() caches one DataStore per (Context, name) pair — without this, a
    // value written by one test is still on disk for the next one.
    @Before
    fun clearPersistedState(): Unit = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @Test
    fun `defaults to SYSTEM when nothing was ever written`() = runTest {
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
    }

    @Test
    fun `written mode is read back`() = runTest {
        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
    }

    @Test
    fun `overwriting a mode replaces it rather than accumulating`() = runTest {
        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, preferences.themeMode.first())
    }

    @Test
    fun `an unknown stored value falls back to SYSTEM instead of crashing`() = runTest {
        context.settingsDataStore.edit { it[THEME_MODE_KEY] = "NOT_A_REAL_THEME_MODE" }

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
    }

    @Test
    fun `includeMegaDynamax defaults to false and round-trips`() = runTest {
        assertEquals(false, preferences.includeMegaDynamax.first())

        preferences.setIncludeMegaDynamax(true)

        assertEquals(true, preferences.includeMegaDynamax.first())
    }

    @Test
    fun `excludeLegendaries defaults to false and round-trips`() = runTest {
        assertEquals(false, preferences.excludeLegendaries.first())

        preferences.setExcludeLegendaries(true)

        assertEquals(true, preferences.excludeLegendaries.first())
    }

    @Test
    fun `includeCustomsAnalysis defaults to false and round-trips`() = runTest {
        assertEquals(false, preferences.includeCustomsAnalysis.first())

        preferences.setIncludeCustomsAnalysis(true)

        assertEquals(true, preferences.includeCustomsAnalysis.first())
    }
}
