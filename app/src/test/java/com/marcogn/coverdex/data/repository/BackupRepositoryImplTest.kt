package com.marcogn.coverdex.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `docs/plan/phase-5-import-export-and-settings.md` §"Tests": a restore replaces existing data
 * entirely (ids preserved) and never touches the Pokédex cache.
 */
// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryImplTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var teamRepository: TeamRepositoryImpl
    private lateinit var customPokemonRepository: CustomPokemonRepositoryImpl
    private lateinit var backupRepository: BackupRepositoryImpl

    @Before
    fun createDependencies() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepositoryImpl(database.teamDao())
        customPokemonRepository = CustomPokemonRepositoryImpl(database.customPokemonDao())
        backupRepository = BackupRepositoryImpl(teamRepository, customPokemonRepository, database.backupDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun pikachu() = TeamMember(
        id = "member-pikachu", pokedexId = 25, speciesName = "Pikachu",
        types = PokemonType.ELECTRIC to null, ability = "Static", moves = List(4) { null }, isCustomSaved = false,
    )

    @Test
    fun `export then import round-trips a team and the custom roster`() = runTest {
        val teamId = teamRepository.createTeam("Kanto Starters")
        teamRepository.saveMember(teamId, 0, pikachu())
        customPokemonRepository.save(pikachu().copy(id = "custom-pikachu", isCustomSaved = true))

        val payload = backupRepository.exportPayload()
        backupRepository.importPayload(payload)

        val team = teamRepository.team(teamId).first()
        assertNotNull(team)
        assertEquals("Kanto Starters", team!!.name)
        assertEquals("Pikachu", team.members[0]?.speciesName)
        assertEquals(1, customPokemonRepository.roster.first().size)
    }

    @Test
    fun `importing a payload fully replaces existing teams, preserving ids`() = runTest {
        val originalTeamId = teamRepository.createTeam("Original")
        teamRepository.saveMember(originalTeamId, 0, pikachu())
        val payload = backupRepository.exportPayload()

        // Simulate data drifting after the backup was taken.
        teamRepository.renameTeam(originalTeamId, "Renamed after backup")
        teamRepository.createTeam("A second team not in the backup")

        backupRepository.importPayload(payload)

        val teams = teamRepository.teams.first()
        assertEquals(1, teams.size)
        assertEquals(originalTeamId, teams[0].id)
        assertEquals("Original", teams[0].name)
    }

    @Test
    fun `a restore never touches the Pokedex cache`() = runTest {
        database.pokedexDao().upsertMeta(
            PokeCacheMetaEntity(id = 1, schemaVersion = 1, datasetRevision = "test-rev", syncedAtEpochMillis = 123L, speciesCount = 10, moveCount = 5),
        )
        val teamId = teamRepository.createTeam("Kanto Starters")
        teamRepository.saveMember(teamId, 0, pikachu())
        val payload = backupRepository.exportPayload()

        backupRepository.importPayload(payload)

        val meta = database.pokedexDao().getMeta()
        assertNotNull(meta)
        assertEquals("test-rev", meta!!.datasetRevision)
        assertEquals(10, meta.speciesCount)
    }
}
