package com.marcogn.coverdex.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.marcogn.coverdex.data.local.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CoverDexDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `a v2 database opens at v3 with existing rows intact, new columns null-or-default, new tables present and empty`() {
        val dbName = "migration-2-3-test"
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO poke_cache_meta (id, schemaVersion, datasetRevision, syncedAtEpochMillis, speciesCount, moveCount) " +
                    "VALUES (1, 1, 'abc123', 1000, 5, 5)",
            )
            execSQL(
                "INSERT INTO poke_species (id, name, displayName, searchName, speciesId, speciesName, type1, type2, " +
                    "isLegendary, isMythical, isFinalEvolution, generationIntroduced, defaultAbility, isDefaultForm) " +
                    "VALUES (1, 'bulbasaur', 'Bulbasaur', 'bulbasaur', 1, 'bulbasaur', 'grass', 'poison', 0, 0, 0, 1, 'overgrow', 1)",
            )
            execSQL(
                "INSERT INTO team (id, name, createdAtEpochMillis, position) VALUES ('t1', 'My Team', 1000, 0)",
            )
            execSQL(
                "INSERT INTO team_member (id, teamId, slotIndex, pokedexId, speciesName, type1, type2, ability, isCustomSaved) " +
                    "VALUES ('m1', 't1', 0, 1, 'Bulbasaur', 'grass', 'poison', 'overgrow', 0)",
            )
            execSQL(
                "INSERT INTO custom_pokemon (id, name, type1, type2, ability, createdAtEpochMillis) " +
                    "VALUES ('c1', 'Custom Mon', 'fire', NULL, NULL, 2000)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        // Existing rows survive, with the new poke_species column defaulting to 0.
        migrated.query("SELECT baseStatTotal FROM poke_species WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT name FROM poke_species WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("bulbasaur", cursor.getString(0))
        }

        // The new team_member/custom_pokemon `item` column is null for pre-existing rows.
        migrated.query("SELECT item FROM team_member WHERE id = 'm1'").use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }
        migrated.query("SELECT item FROM custom_pokemon WHERE id = 'c1'").use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }

        // The two new cache tables exist and are queryable (empty, but present).
        for (table in listOf("poke_pokemon_ability", "poke_species_bst_past")) {
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("table $table should be empty but present", 0, cursor.getInt(0))
            }
        }
    }
}
