package com.marcogn.coverdex.domain.pokeapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each fixture in `src/test/resources/csv/` is a handful of real rows taken from the pinned
 * dataset revision (docs/plan/reference-pokedata.md §6), not invented data — including the
 * awkward cases: a form with id > 10000 (`deoxys-attack`), a null-power move (`horn-drill`, an
 * OHKO move), and a species with no `evolves_from_species_id` alongside one that has it.
 */
class DatasetParsersTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("csv/$name")) { "missing fixture: $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun `parses pokemon csv, including a form id greater than 10000`() {
        val rows = parsePokemon(fixture("pokemon.csv"))

        val bulbasaur = rows.first { it.id == 1 }
        assertEquals("bulbasaur", bulbasaur.identifier)
        assertEquals(1, bulbasaur.speciesId)
        assertTrue(bulbasaur.isDefault)

        val form = rows.first { it.id == 10001 }
        assertEquals("deoxys-attack", form.identifier)
        assertEquals(386, form.speciesId)
        assertTrue(!form.isDefault)
    }

    @Test
    fun `parses pokemon species csv, evolves_from_species_id empty means null`() {
        val rows = parseSpecies(fixture("pokemon_species.csv"))

        val bulbasaur = rows.first { it.id == 1 }
        assertNull(bulbasaur.evolvesFromSpeciesId)

        val ivysaur = rows.first { it.id == 2 }
        assertEquals(1, ivysaur.evolvesFromSpeciesId)

        val articuno = rows.first { it.id == 144 }
        assertTrue(articuno.isLegendary)
        assertTrue(!articuno.isMythical)
    }

    @Test
    fun `parses pokemon types csv ordered by slot`() {
        val rows = parsePokemonTypes(fixture("pokemon_types.csv"))
        val bulbasaurTypes = rows.filter { it.pokemonId == 1 }.sortedBy { it.slot }

        assertEquals(2, bulbasaurTypes.size)
        assertEquals(12, bulbasaurTypes[0].typeId)
        assertEquals(4, bulbasaurTypes[1].typeId)
    }

    @Test
    fun `parses pokemon abilities csv, is_hidden as boolean`() {
        val rows = parsePokemonAbilities(fixture("pokemon_abilities.csv"))
        val bulbasaurAbilities = rows.filter { it.pokemonId == 1 }

        val nonHidden = bulbasaurAbilities.first { !it.isHidden }
        assertEquals(65, nonHidden.abilityId)
        assertEquals(1, nonHidden.slot)

        val hidden = bulbasaurAbilities.first { it.isHidden }
        assertEquals(34, hidden.abilityId)
    }

    @Test
    fun `a form with no pokemon_abilities row at all has an empty result set`() {
        val rows = parsePokemonAbilities(fixture("pokemon_abilities.csv"))

        assertTrue(rows.none { it.pokemonId == 10301 })
    }

    @Test
    fun `parses abilities csv`() {
        val rows = parseAbilities(fixture("abilities.csv"))

        assertEquals("overgrow", rows.first { it.id == 65 }.identifier)
        assertEquals("aura-break", rows.first { it.id == 188 }.identifier)
    }

    @Test
    fun `parses moves csv, empty power means null not zero`() {
        val rows = parseMoves(fixture("moves.csv"))

        val pound = rows.first { it.id == 1 }
        assertEquals(40, pound.power)
        assertEquals(2, pound.damageClassId)

        val hornDrill = rows.first { it.id == 32 }
        assertNull(hornDrill.power)
        assertEquals(2, hornDrill.damageClassId)

        val splash = rows.first { it.id == 150 }
        assertNull(splash.power)
        assertEquals(1, splash.damageClassId)
    }

    @Test
    fun `parses types csv including the ids outside the 18 real types`() {
        val rows = parseTypes(fixture("types.csv"))

        assertEquals(21, rows.size)
        assertEquals("normal", rows.first { it.id == 1 }.identifier)
        assertEquals("fairy", rows.first { it.id == 18 }.identifier)
        assertEquals("stellar", rows.first { it.id == 19 }.identifier)
        assertEquals("unknown", rows.first { it.id == 10001 }.identifier)
        assertEquals("shadow", rows.first { it.id == 10002 }.identifier)
    }

    @Test
    fun `parses type efficacy csv across all four factor values`() {
        val rows = parseTypeEfficacy(fixture("type_efficacy.csv"))
        val factors = rows.map { it.damageFactor }.toSet()

        assertEquals(setOf(0, 50, 100, 200), factors)
    }

    @Test
    fun `parses pokemon stats csv into six rows per form`() {
        val rows = parsePokemonStats(fixture("pokemon_stats.csv"))
        val bulbasaurStats = rows.filter { it.pokemonId == 1 }.associate { it.statId to it.baseStat }

        assertEquals(mapOf(1 to 45, 2 to 49, 3 to 49, 4 to 65, 5 to 65, 6 to 45), bulbasaurStats)
    }

    @Test
    fun `parses pokemon stats past csv, generationId is the last generation the value held`() {
        val rows = parsePokemonStatsPast(fixture("pokemon_stats_past.csv"))

        val deoxysAttack = rows.first { it.pokemonId == 10001 }
        assertEquals(5, deoxysAttack.generationId)
        assertEquals(4, deoxysAttack.statId)
        assertEquals(150, deoxysAttack.baseStat)
    }

    @Test
    fun `parses ability names csv, keeping only the English rows`() {
        val rows = parseAbilityNames(fixture("ability_names.csv"))

        assertEquals("Overgrow", rows.first { it.id == 65 }.name)
        assertEquals("Well-Baked Body", rows.first { it.id == 202 }.name)
        assertEquals(3, rows.size)
    }

    @Test
    fun `parses move names csv, keeping only the English rows`() {
        val rows = parseMoveNames(fixture("move_names.csv"))

        assertEquals("Pound", rows.first { it.id == 1 }.name)
        assertEquals("Double-Edge", rows.first { it.id == 250 }.name)
        assertEquals(4, rows.size)
    }
}
