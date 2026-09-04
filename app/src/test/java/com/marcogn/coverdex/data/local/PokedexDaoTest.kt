package com.marcogn.coverdex.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.dao.PokedexDao
import com.marcogn.coverdex.data.local.entity.PokeAbilityEntity
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.data.local.entity.PokeMoveEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity
import com.marcogn.coverdex.data.local.entity.TypeEfficacyEntity
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

// sdk = 26: see CLAUDE.md, "Known gotchas" — Robolectric's shadow jar for compileSdk 36 needs a
// newer JDK than CI runs. This app's own minSdk stays 24; the pin is only about Robolectric.
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class PokedexDaoTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var dao: PokedexDao

    private fun species(
        id: Int,
        name: String,
        isDefaultForm: Boolean = true,
    ) = PokeSpeciesEntity(
        id = id,
        name = name,
        displayName = name,
        searchName = com.marcogn.coverdex.domain.pokeapi.searchKey(name),
        speciesId = id,
        speciesName = name,
        type1 = "normal",
        type2 = null,
        isLegendary = false,
        isMythical = false,
        isFinalEvolution = true,
        generationIntroduced = 1,
        defaultAbility = null,
        isDefaultForm = isDefaultForm,
    )

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
    fun `inserted species round-trips through getSpeciesById`() = runTest {
        dao.replaceAllSpecies(listOf(species(1, "bulbasaur")))

        val loaded = dao.getSpeciesById(1)

        assertEquals("bulbasaur", loaded?.name)
    }

    @Test
    fun `search matches mrmime, mr mime and mr-mime spellings alike`() = runTest {
        dao.replaceAllSpecies(listOf(species(122, "mr-mime")))

        for (typed in listOf("mrmime", "mr mime", "mr-mime", "MR-MIME")) {
            val key = com.marcogn.coverdex.domain.pokeapi.searchKey(typed)
            val results = dao.searchSpecies(key, limit = -1).first()
            assertEquals("no match for '$typed' (normalized '$key')", 1, results.size)
        }
    }

    @Test
    fun `a blank search key is the repository's job to reject, the DAO itself just matches everything`() = runTest {
        // PokedexRepository enforces "blank query -> empty" (native-spec.md's dropdown contract);
        // the DAO has no opinion on blank input, it is a LIKE '%%' match, so this asserts the DAO
        // layer's actual (permissive) behaviour rather than the contract the repository adds.
        dao.replaceAllSpecies(listOf(species(1, "bulbasaur"), species(2, "ivysaur")))

        val results = dao.searchSpecies("", limit = -1).first()

        assertEquals(2, results.size)
    }

    @Test
    fun `prefix matches rank before contains-only matches`() = runTest {
        // "char" is a prefix of "charmander" but only a substring of "scharm" (contrived, but
        // proves the CASE-based ranking rather than relying on real data having no such overlap).
        dao.replaceAllSpecies(listOf(species(1, "scharm"), species(2, "charmander")))

        val results = dao.searchSpecies("char", limit = -1).first()

        assertEquals(listOf("charmander", "scharm"), results.map { it.name })
    }

    @Test
    fun `a species default form ranks before its alternate forms on a tie`() = runTest {
        dao.replaceAllSpecies(
            listOf(
                species(10301, "zygarde-mega", isDefaultForm = false),
                species(718, "zygarde-50", isDefaultForm = true),
            ),
        )

        val results = dao.searchSpecies("zygarde", limit = -1).first()

        assertEquals(listOf("zygarde-50", "zygarde-mega"), results.map { it.name })
    }

    @Test
    fun `limit -1 returns every match with no cap`() = runTest {
        dao.replaceAllSpecies((1..50).map { species(it, "species$it") })

        val results = dao.searchSpecies("species", limit = -1).first()

        assertEquals(50, results.size)
    }

    @Test
    fun `a positive limit caps the results`() = runTest {
        dao.replaceAllSpecies((1..50).map { species(it, "species$it") })

        val results = dao.searchSpecies("species", limit = 5).first()

        assertEquals(5, results.size)
    }

    @Test
    fun `replacing all species clears the previous set rather than accumulating`() = runTest {
        dao.replaceAllSpecies(listOf(species(1, "bulbasaur")))
        dao.replaceAllSpecies(listOf(species(2, "ivysaur")))

        assertNull(dao.getSpeciesById(1))
        assertEquals("ivysaur", dao.getSpeciesById(2)?.name)
    }

    @Test
    fun `moves and abilities round-trip and search the same way`() = runTest {
        dao.replaceAllMoves(
            listOf(
                PokeMoveEntity(id = 1, name = "pound", displayName = "Pound", searchName = "pound", typeName = "normal", power = 40, damageClass = "PHYSICAL"),
            ),
        )
        dao.replaceAllAbilities(
            listOf(PokeAbilityEntity(id = 65, name = "overgrow", displayName = "Overgrow", searchName = "overgrow")),
        )

        assertEquals(1, dao.searchMoves("pound", limit = -1).first().size)
        assertEquals(1, dao.searchAbilities("overgrow", limit = -1).first().size)
    }

    @Test
    fun `type efficacy round-trips`() = runTest {
        dao.replaceAllTypeEfficacy(
            listOf(
                TypeEfficacyEntity(attacker = "fire", defender = "grass", factor = 2.0),
                TypeEfficacyEntity(attacker = "fire", defender = "water", factor = 0.5),
            ),
        )

        val all = dao.getAllTypeEfficacy()

        assertEquals(2, all.size)
        assertEquals(2.0, all.first { it.defender == "grass" }.factor, 0.0)
    }

    @Test
    fun `replaceCache writes every table plus meta in one call`() = runTest {
        dao.replaceCache(
            species = listOf(species(1, "bulbasaur")),
            moves = listOf(PokeMoveEntity(1, "pound", "Pound", "pound", "normal", 40, "PHYSICAL")),
            abilities = listOf(PokeAbilityEntity(65, "overgrow", "Overgrow", "overgrow")),
            typeEfficacy = listOf(TypeEfficacyEntity("fire", "grass", 2.0)),
            meta = PokeCacheMetaEntity(schemaVersion = 1, datasetRevision = "abc123", syncedAtEpochMillis = 1000L, speciesCount = 1, moveCount = 1),
        )

        assertEquals("bulbasaur", dao.getSpeciesById(1)?.name)
        assertEquals(1, dao.getMeta()?.speciesCount)
    }

    @Test
    fun `clearCache wipes every cache table by name`() = runTest {
        dao.replaceCache(
            species = listOf(species(1, "bulbasaur")),
            moves = listOf(PokeMoveEntity(1, "pound", "Pound", "pound", "normal", 40, "PHYSICAL")),
            abilities = listOf(PokeAbilityEntity(65, "overgrow", "Overgrow", "overgrow")),
            typeEfficacy = listOf(TypeEfficacyEntity("fire", "grass", 2.0)),
            meta = PokeCacheMetaEntity(schemaVersion = 1, datasetRevision = "abc123", syncedAtEpochMillis = 1000L, speciesCount = 1, moveCount = 1),
        )

        dao.clearCache()

        assertNull(dao.getSpeciesById(1))
        assertTrue(dao.searchMoves("pound", limit = -1).first().isEmpty())
        assertTrue(dao.searchAbilities("overgrow", limit = -1).first().isEmpty())
        assertTrue(dao.getAllTypeEfficacy().isEmpty())
        assertNull(dao.getMeta())
    }
}
