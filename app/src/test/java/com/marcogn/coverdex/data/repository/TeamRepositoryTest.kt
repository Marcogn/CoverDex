package com.marcogn.coverdex.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class TeamRepositoryTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var repository: TeamRepositoryImpl

    private fun move(name: String) =
        PokemonMove(id = java.util.UUID.randomUUID().toString(), name = name, type = PokemonType.NORMAL, power = 40, damageClass = DamageClass.PHYSICAL, isCustom = false)

    private fun member(speciesName: String, moves: List<PokemonMove?> = listOf(move("tackle"), null, null, null)) = TeamMember(
        id = "member-$speciesName",
        pokedexId = 1,
        speciesName = speciesName,
        types = PokemonType.GRASS to PokemonType.POISON,
        ability = "overgrow",
        moves = moves,
        isCustomSaved = false,
    )

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TeamRepositoryImpl(database.teamDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `saving a member into one slot replaces exactly that slot's moves, not another slot's`() = runTest {
        val teamId = repository.createTeam("My Team")
        repository.saveMember(teamId, 0, member("bulbasaur"))
        repository.saveMember(teamId, 1, member("charmander"))

        repository.saveMember(teamId, 0, member("squirtle", moves = listOf(move("water-gun"), move("tackle"), null, null)))

        val team = repository.team(teamId).first()
        assertEquals("squirtle", team?.members?.get(0)?.speciesName)
        assertEquals(listOf("water-gun", "tackle"), team?.members?.get(0)?.moves?.filterNotNull()?.map { it.name })
        assertEquals("charmander", team?.members?.get(1)?.speciesName)
        assertEquals(1, team?.members?.get(1)?.moves?.filterNotNull()?.size)
    }

    @Test
    fun `clearing a slot leaves the other five untouched`() = runTest {
        val teamId = repository.createTeam("My Team")
        for (slot in 0..5) repository.saveMember(teamId, slot, member("species$slot"))

        repository.clearSlot(teamId, 2)

        val team = repository.team(teamId).first()
        assertEquals(6, team?.members?.size)
        assertNull(team?.members?.get(2))
        for (slot in listOf(0, 1, 3, 4, 5)) {
            assertEquals("species$slot", team?.members?.get(slot)?.speciesName)
        }
    }

    @Test
    fun `wiping the Pokedex cache leaves every saved team byte-identical`() = runTest {
        // The denormalization invariant (docs/plan/native-spec.md, "Storage"): a team slot's
        // species/type/ability data is a snapshot, never a reference into the cache.
        database.pokedexDao().replaceCache(
            species = listOf(
                PokeSpeciesEntity(
                    id = 1, name = "bulbasaur", displayName = "Bulbasaur", searchName = "bulbasaur",
                    speciesId = 1, speciesName = "bulbasaur", type1 = "grass", type2 = "poison",
                    isLegendary = false, isMythical = false, isFinalEvolution = false,
                    generationIntroduced = 1, defaultAbility = "overgrow", isDefaultForm = true,
                ),
            ),
            moves = emptyList(),
            abilities = emptyList(),
            typeEfficacy = emptyList(),
            pokemonAbilities = emptyList(),
            bstPast = emptyList(),
            meta = PokeCacheMetaEntity(schemaVersion = 1, datasetRevision = "abc123", syncedAtEpochMillis = 1000L, speciesCount = 1, moveCount = 0),
        )
        val teamId = repository.createTeam("My Team")
        repository.saveMember(teamId, 0, member("bulbasaur"))
        val before = repository.team(teamId).first()

        database.pokedexDao().clearCache()

        val after = repository.team(teamId).first()
        assertEquals(before, after)
    }
}
