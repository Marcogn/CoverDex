package com.marcogn.coverdex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `valueOf round-trips every entry`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.valueOf(mode.name))
        }
    }

    @Test
    fun `valueOf throws on an unknown name rather than returning a default`() {
        // ThemePreferences relies on this throwing so it can fall back to SYSTEM itself
        // (runCatching { ThemeMode.valueOf(stored) }.getOrNull() ?: ThemeMode.SYSTEM) — see
        // ThemePreferencesTest's "an unknown stored value falls back to SYSTEM" case.
        assertThrows(IllegalArgumentException::class.java) {
            ThemeMode.valueOf("NOT_A_REAL_THEME_MODE")
        }
    }
}
