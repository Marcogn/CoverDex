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
            pokemonStatsCsv = fixture("pokemon_stats.csv"),
            pokemonStatsPastCsv = fixture("pokemon_stats_past.csv"),
            abilityNamesCsv = fixture("ability_names.csv"),
            moveNamesCsv = fixture("move_names.csv"),
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
    fun `default ability resolves to the lowest-slot non-hidden ability, as a display name`() {
        val bulbasaur = dataset.species.first { it.id == 1 }

        // slot 1 non-hidden = overgrow (65); slot 3 hidden = chlorophyll (34), not picked. The
        // value is the display name ("Overgrow"), not the raw slug — Phase 7 fixed
        // defaultAbility carrying the PokeAPI identifier verbatim (phase-7-...md §0.2).
        assertEquals("Overgrow", bulbasaur.defaultAbility)
    }

    @Test
    fun `a form with no pokemon_abilities row falls back to its species default form`() {
        val zygardeMega = dataset.species.first { it.id == 10301 }

        // 10301 (zygarde-mega) has no ability rows of its own; its species (718) default form
        // (718, zygarde-50) resolves to aura-break (188), shown as "Aura Break".
        assertEquals("Aura Break", zygardeMega.defaultAbility)
    }

    @Test
    fun `isFinalEvolution is false for a species another species evolves from`() {
        // ivysaur (species 2) has evolves_from_species_id = 1, so bulbasaur (species 1) is not final.
        val bulbasaur = dataset.species.first { it.id == 1 }

        assertTrue(!bulbasaur.isFinalEvolution)
    }

    @Test
    fun `isDefaultForm reflects pokemon csv's is_default column`() {
        val zygarde50 = dataset.species.first { it.id == 718 }
        assertTrue(zygarde50.isDefaultForm)

        val zygardeMega = dataset.species.first { it.id == 10301 }
        assertTrue(!zygardeMega.isDefaultForm)
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
    fun `abilities list is built from ability_names csv, not prettify`() {
        val overgrow = dataset.abilities.first { it.id == 65 }
        assertEquals("overgrow", overgrow.name)
        assertEquals("Overgrow", overgrow.displayName)

        // well-baked-body is the case prettify() gets wrong ("Well Baked Body") — the real name,
        // from ability_names.csv, keeps the hyphen. See phase-7-...md §0.3.
        val wellBakedBody = dataset.abilities.first { it.id == 202 }
        assertEquals("Well-Baked Body", wellBakedBody.displayName)
    }

    @Test
    fun `an ability absent from ability_names csv falls back to prettify`() {
        // chlorophyll (34) is deliberately absent from the trimmed ability_names.csv fixture.
        val chlorophyll = dataset.abilities.first { it.id == 34 }
        assertEquals(prettify("chlorophyll"), chlorophyll.displayName)
    }

    @Test
    fun `moves list is built from move_names csv, not prettify`() {
        val doubleEdge = dataset.moves.first { it.id == 250 }
        // prettify("double-edge") would give "Double Edge" — the real name keeps the hyphen.
        assertEquals("Double-Edge", doubleEdge.displayName)
    }

    @Test
    fun `baseStatTotal is the sum of the six current stats`() {
        val bulbasaur = dataset.species.first { it.id == 1 }
        assertEquals(45 + 49 + 49 + 65 + 65 + 45, bulbasaur.baseStatTotal)

        val deoxysAttack = dataset.species.first { it.id == 10001 }
        assertEquals(50 + 180 + 20 + 180 + 20 + 150, deoxysAttack.baseStatTotal)
    }

    @Test
    fun `a form with no pokemon_stats row has baseStatTotal zero, not a crash`() {
        // id 999 ("teststat") has a pokemon.csv/pokemon_types.csv row but no pokemon_stats.csv
        // row at all.
        val noStats = dataset.species.first { it.id == 999 }
        assertEquals(0, noStats.baseStatTotal)
    }

    @Test
    fun `pastBst carries the generation-1 five-stat total, always emitted when a special row exists`() {
        val articunoGen1 = dataset.pastBst.first { it.pokemonId == 144 && it.generationId == 1 }

        // hp(90) + attack(85) + defense(100) + speed(85) + special(100, from the past row) = 460,
        // NOT the current six-stat 580 — Gen I has no Sp.Atk/Sp.Def split.
        assertEquals(460, articunoGen1.bst)
    }

    @Test
    fun `pastBst carries a later-generation stat change when the total actually differs`() {
        val deoxysAttackGen5 = dataset.pastBst.first { it.pokemonId == 10001 && it.generationId == 5 }

        // Special Attack was 150 through gen 5 (currently 180); every other stat unchanged.
        assertEquals(50 + 180 + 20 + 150 + 20 + 150, deoxysAttackGen5.bst)
    }

    @Test
    fun `a form with no historical stat changes has no pastBst rows`() {
        assertTrue(dataset.pastBst.none { it.pokemonId == 1 })
        assertTrue(dataset.pastBst.none { it.pokemonId == 718 })
    }

    @Test
    fun `pokemonAbilities carries every canonical ability row with its display name`() {
        val bulbasaurAbilities = dataset.pokemonAbilities.filter { it.pokemonId == 1 }

        assertEquals(2, bulbasaurAbilities.size)
        val nonHidden = bulbasaurAbilities.first { !it.isHidden }
        assertEquals("overgrow", nonHidden.slug)
        assertEquals("Overgrow", nonHidden.displayName)
        assertEquals(1, nonHidden.slot)

        val hidden = bulbasaurAbilities.first { it.isHidden }
        assertEquals("chlorophyll", hidden.slug)
        assertEquals("Chlorophyll", hidden.displayName)
    }

    @Test
    fun `prettify splits on hyphens and capitalizes each part`() {
        assertEquals("Mr Mime", prettify("mr-mime"))
        assertEquals("Fire Punch", prettify("fire-punch"))
    }

    @Test
    fun `every assembled form has a valid typing - non-null type1, and type2 never equal to it`() {
        // Cheap structural guard for the invariant §7.3.3 asks for (the real 171-typing coverage
        // lives in CoverageEngineTest against the full type space, not against 1351+ forms —
        // see phase-7-accuracy-and-customization.md §7.3.2's own reasoning for why).
        for (species in dataset.species) {
            assertTrue(PokemonType.entries.contains(species.types.first))
            assertTrue(species.types.second == null || species.types.second != species.types.first)
        }
    }

    @Test
    fun `searchKey normalizes hyphens, spaces and case away`() {
        assertEquals("mrmime", searchKey("mr-mime"))
        assertEquals("mrmime", searchKey("Mr Mime"))
        assertEquals("mrmime", searchKey("MR-MIME"))
    }
}
