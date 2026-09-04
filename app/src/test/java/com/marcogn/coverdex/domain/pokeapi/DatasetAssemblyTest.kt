package com.marcogn.coverdex.domain.pokeapi

import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises docs/plan/reference-pokedata.md §3's joins against the same fixtures as
 * [DatasetParsersTest] — real rows from the pinned dataset revision. */
class DatasetAssemblyTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("csv/$name")) { "missing fixture: $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val dataset by lazy {
        assembleDataset(
            pokemonCsv = fixture("pokemon.csv"),
            speciesCsv = fixture("pokemon_species.csv"),
            pokemonTypesCsv = fixture("pokemon_types.csv"),
            pokemonAbilitiesCsv = fixture("pokemon_abilities.csv"),
            abilitiesCsv = fixture("abilities.csv"),
            movesCsv = fixture("moves.csv"),
            typesCsv = fixture("types.csv"),
            typeEfficacyCsv = fixture("type_efficacy.csv"),
        )
    }

    @Test
    fun `a dual-type form gets its types in slot order`() {
        val bulbasaur = dataset.species.first { it.id == 1 }

        assertEquals(PokemonType.GRASS to PokemonType.POISON, bulbasaur.types)
    }

    @Test
    fun `a single-type form has a null second type, not a placeholder`() {
        val deoxysAttack = dataset.species.first { it.id == 10001 }

        assertEquals(PokemonType.PSYCHIC, deoxysAttack.types.first)
        assertNull(deoxysAttack.types.second)
    }

    @Test
    fun `default ability resolves to the lowest-slot non-hidden ability`() {
        val bulbasaur = dataset.species.first { it.id == 1 }

        // slot 1 non-hidden = overgrow (65); slot 3 hidden = chlorophyll (34), not picked.
        assertEquals("overgrow", bulbasaur.defaultAbility)
    }

    @Test
    fun `a form with no pokemon_abilities row falls back to its species default form`() {
        val zygardeMega = dataset.species.first { it.id == 10301 }

        // 10301 (zygarde-mega) has no ability rows of its own; its species (718) default form
        // (718, zygarde-50) resolves to aura-break (188).
        assertEquals("aura-break", zygardeMega.defaultAbility)
    }

    @Test
    fun `isFinalEvolution is false for a species another species evolves from`() {
        // ivysaur (species 2) has evolves_from_species_id = 1, so bulbasaur (species 1) is not final.
        val bulbasaur = dataset.species.first { it.id == 1 }

        assertTrue(!bulbasaur.isFinalEvolution)
    }

    @Test
    fun `isFinalEvolution is true for a species nothing evolves from`() {
        val zygarde = dataset.species.first { it.id == 718 }

        assertTrue(zygarde.isFinalEvolution)
    }

    @Test
    fun `legendary and mythical flags come from the species row`() {
        val articuno = dataset.species.first { it.id == 144 }
        assertTrue(articuno.isLegendary)
        assertTrue(!articuno.isMythical)

        val deoxysAttack = dataset.species.first { it.id == 10001 }
        assertTrue(!deoxysAttack.isLegendary)
        assertTrue(deoxysAttack.isMythical)
    }

    @Test
    fun `moves carry a null power rather than zero for status and fixed-damage moves`() {
        val hornDrill = dataset.moves.first { it.id == 32 }
        assertNull(hornDrill.power)
        assertEquals(DamageClass.PHYSICAL, hornDrill.damageClass)

        val splash = dataset.moves.first { it.id == 150 }
        assertNull(splash.power)
        assertEquals(DamageClass.STATUS, splash.damageClass)

        val pound = dataset.moves.first { it.id == 1 }
        assertEquals(40, pound.power)
        assertEquals(DamageClass.PHYSICAL, pound.damageClass)
    }

    @Test
    fun `the type chart maps every damage factor onto the right multiplier`() {
        // From the fixture: normal(1)->ghost(8)=0, fighting(2)->ghost(8)=0,
        // normal(1)->rock(6)=50, normal(1)->steel(9)=50,
        // normal(1)->normal(1)=100, normal(1)->fighting(2)=100,
        // fighting(2)->normal(1)=200, fighting(2)->rock(6)=200.
        assertEquals(0.0, dataset.typeChart.multiplier(PokemonType.NORMAL, PokemonType.GHOST), 0.0)
        assertEquals(0.5, dataset.typeChart.multiplier(PokemonType.NORMAL, PokemonType.ROCK), 0.0)
        assertEquals(1.0, dataset.typeChart.multiplier(PokemonType.NORMAL, PokemonType.NORMAL), 0.0)
        assertEquals(2.0, dataset.typeChart.multiplier(PokemonType.FIGHTING, PokemonType.NORMAL), 0.0)
    }

    @Test
    fun `an untabulated pair defaults to neutral rather than throwing`() {
        assertEquals(1.0, dataset.typeChart.multiplier(PokemonType.WATER, PokemonType.FAIRY), 0.0)
    }

    @Test
    fun `abilities list is built from abilities csv with prettified display names`() {
        val overgrow = dataset.abilities.first { it.id == 65 }

        assertEquals("overgrow", overgrow.name)
        assertEquals("Overgrow", overgrow.displayName)
    }

    @Test
    fun `prettify splits on hyphens and capitalizes each part`() {
        assertEquals("Mr Mime", prettify("mr-mime"))
        assertEquals("Fire Punch", prettify("fire-punch"))
    }

    @Test
    fun `searchKey normalizes hyphens, spaces and case away`() {
        assertEquals("mrmime", searchKey("mr-mime"))
        assertEquals("mrmime", searchKey("Mr Mime"))
        assertEquals("mrmime", searchKey("MR-MIME"))
    }
}
