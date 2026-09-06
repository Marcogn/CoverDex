package com.marcogn.coverdex.data.pokeapi

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.local.dao.PokedexDao
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.pokeapi.DATASET_SCHEMA_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.classLoader?.getResourceAsStream("csv/$name")) { "missing fixture: $name" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

/** Returns the same real fixture rows [DatasetAssemblyTest] uses (a handful of real rows from
 * the pinned dataset revision), or throws for every file if [shouldFail] is set — no mocking
 * library needed for either case, see [DatasetSource]'s doc. */
private class FakeDatasetSource(private val shouldFail: Boolean = false) : DatasetSource {
    override suspend fun fetchAll(): Map<DatasetFile, String> {
        if (shouldFail) throw java.io.IOException("simulated network failure")
        return mapOf(
            DatasetFile.POKEMON to fixture("pokemon.csv"),
            DatasetFile.SPECIES to fixture("pokemon_species.csv"),
            DatasetFile.POKEMON_TYPES to fixture("pokemon_types.csv"),
            DatasetFile.POKEMON_ABILITIES to fixture("pokemon_abilities.csv"),
            DatasetFile.ABILITIES to fixture("abilities.csv"),
            DatasetFile.MOVES to fixture("moves.csv"),
            DatasetFile.TYPES to fixture("types.csv"),
            DatasetFile.TYPE_EFFICACY to fixture("type_efficacy.csv"),
            DatasetFile.POKEMON_STATS to fixture("pokemon_stats.csv"),
            DatasetFile.POKEMON_STATS_PAST to fixture("pokemon_stats_past.csv"),
            DatasetFile.ABILITY_NAMES to fixture("ability_names.csv"),
            DatasetFile.MOVE_NAMES to fixture("move_names.csv"),
        )
    }
}

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class DatasetSyncManagerTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var dao: PokedexDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.pokedexDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a successful sync writes the meta row and every cache table`() = runTest {
        val manager = DatasetSyncManager(client = FakeDatasetSource(), pokedexDao = dao, scope = this)

        manager.startIfNeeded()

        val terminal = manager.state.first { it is SyncState.Success || it is SyncState.Failed }
        assertTrue("expected Success, got $terminal", terminal is SyncState.Success)

        val meta = dao.getMeta()
        assertEquals(DATASET_SCHEMA_VERSION, meta?.schemaVersion)
        assertEquals(DATASET_REVISION, meta?.datasetRevision)
        assertTrue(meta!!.speciesCount > 0)
        assertTrue(meta.moveCount > 0)
        assertTrue(dao.getAllSpecies().isNotEmpty())
    }

    @Test
    fun `a failing fetch leaves the database untouched and reports Failed`() = runTest {
        val manager = DatasetSyncManager(client = FakeDatasetSource(shouldFail = true), pokedexDao = dao, scope = this)

        manager.startIfNeeded()

        val terminal = manager.state.first { it is SyncState.Success || it is SyncState.Failed }
        assertTrue(terminal is SyncState.Failed)

        assertNull(dao.getMeta())
        assertTrue(dao.getAllSpecies().isEmpty())
    }

    @Test
    fun `a fresh cache short-circuits without touching the network`() = runTest {
        // A meta row matching the current schema version and dataset revision means "already
        // fresh" — startIfNeeded() must not call the (failing) client at all.
        dao.upsertMeta(
            com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity(
                schemaVersion = DATASET_SCHEMA_VERSION,
                datasetRevision = DATASET_REVISION,
                syncedAtEpochMillis = 12345L,
                speciesCount = 1,
                moveCount = 1,
            ),
        )
        val manager = DatasetSyncManager(client = FakeDatasetSource(shouldFail = true), pokedexDao = dao, scope = this)

        manager.startIfNeeded()

        val terminal = manager.state.first { it is SyncState.Success || it is SyncState.Failed }
        assertTrue(terminal is SyncState.Success)
        assertEquals(12345L, (terminal as SyncState.Success).syncedAtEpochMillis)
    }

    @Test
    fun `a schema version mismatch is not fresh, so it re-syncs`() = runTest {
        dao.upsertMeta(
            com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity(
                schemaVersion = DATASET_SCHEMA_VERSION - 1,
                datasetRevision = DATASET_REVISION,
                syncedAtEpochMillis = 1L,
                speciesCount = 1,
                moveCount = 1,
            ),
        )
        val manager = DatasetSyncManager(client = FakeDatasetSource(), pokedexDao = dao, scope = this)

        assertTrue(!manager.isCacheFresh())

        manager.startIfNeeded()
        val terminal = manager.state.first { it is SyncState.Success || it is SyncState.Failed }
        assertTrue(terminal is SyncState.Success)
        assertEquals(DATASET_SCHEMA_VERSION, dao.getMeta()?.schemaVersion)
    }

    @Test
    fun `forceResync re-syncs even when the cache is already fresh`() = runTest {
        dao.upsertMeta(
            com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity(
                schemaVersion = DATASET_SCHEMA_VERSION,
                datasetRevision = DATASET_REVISION,
                syncedAtEpochMillis = 1L,
                speciesCount = 1,
                moveCount = 1,
            ),
        )
        val manager = DatasetSyncManager(client = FakeDatasetSource(), pokedexDao = dao, scope = this)

        manager.forceResync()

        val terminal = manager.state.first { it is SyncState.Success || it is SyncState.Failed }
        assertTrue(terminal is SyncState.Success)
        assertTrue((terminal as SyncState.Success).syncedAtEpochMillis > 1L)
    }
}

