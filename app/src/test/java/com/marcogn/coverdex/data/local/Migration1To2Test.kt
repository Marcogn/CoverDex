package com.marcogn.coverdex.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.marcogn.coverdex.data.local.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CoverDexDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `a v1 database opens at v2 with its cache rows intact and the new tables present`() {
        val dbName = "migration-test"
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO poke_cache_meta (id, schemaVersion, datasetRevision, syncedAtEpochMillis, speciesCount, moveCount) " +
                    "VALUES (1, 1, 'abc123', 1000, 5, 5)",
            )
            execSQL(
                "INSERT INTO poke_species (id, name, displayName, searchName, speciesId, speciesName, type1, type2, " +
                    "isLegendary, isMythical, isFinalEvolution, generationIntroduced, defaultAbility, isDefaultForm) " +
                    "VALUES (1, 'bulbasaur', 'Bulbasaur', 'bulbasaur', 1, 'bulbasaur', 'grass', 'poison', 0, 0, 0, 1, 'overgrow', 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // The Phase 1 cache survives the migration untouched.
        migrated.query("SELECT COUNT(*) FROM poke_species").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT datasetRevision FROM poke_cache_meta WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("abc123", cursor.getString(0))
        }

        // The five new user-data tables exist and are queryable (empty, but present).
        for (table in listOf("team", "team_member", "team_member_move", "custom_pokemon", "custom_pokemon_move")) {
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("table $table should be empty but present", 0, cursor.getInt(0))
            }
        }
    }
}
