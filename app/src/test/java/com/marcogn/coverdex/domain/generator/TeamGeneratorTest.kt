package com.marcogn.coverdex.domain.generator

import com.marcogn.coverdex.domain.buildMember
import com.marcogn.coverdex.domain.mockPokemonList
import com.marcogn.coverdex.domain.mockTypeChart
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Ports `legacy-web/src/hooks/__tests__/{teamGenerator,surpriseMe}.test.ts`. The TypeScript uses
 * `Math.random()` directly and so can only assert probabilistically (e.g. "at least 4 of 5 runs");
 * this port takes an injectable [Random] (`docs/plan/phase-4-suggestions-and-generator.md` §3),
 * so the equivalent cases assert **deterministically** across a fixed set of seeds instead of
 * accepting a flaky minority — a strictly better test than the original.
 */
class TeamGeneratorTest {

    private val chart = mockTypeChart()
    private val pool = mockPokemonList()

    // ---- generateTeam ----

    @Test
    fun `generates a team of up to 6 from the pool when no locked members`() {
        val result = generateTeam(chart, pool, emptyList(), DEFAULT_CONSTRAINTS, Random(1))
        assertTrue(result.team.size <= 6)
        assertTrue(result.team.isNotEmpty())
    }

    @Test
    fun `locked members stay at the start of the team`() {
        val locked = listOf(buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING))
        val result = generateTeam(chart, pool, locked, DEFAULT_CONSTRAINTS, Random(1))
        assertEquals("Charizard", result.team[0].speciesName)
    }

    @Test
    fun `never duplicates a species already among the locked members`() {
        val locked = listOf(buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING))
        val result = generateTeam(chart, pool, locked, DEFAULT_CONSTRAINTS, Random(1))
        val names = result.team.map { it.speciesName.lowercase() }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `returns a tooFewPokemon warning when the pool cannot fill every slot`() {
        val tinyPool = listOf(
            pool[0].copy(isFinalEvolution = true),
            pool[1].copy(isFinalEvolution = true),
        )
        val result = generateTeam(chart, tinyPool, emptyList(), DEFAULT_CONSTRAINTS, Random(1))
        assertEquals("tooFewPokemon", result.warning)
        assertTrue(result.team.size < 6)
    }

    @Test
    fun `six locked members are returned as-is`() {
        val locked = (0 until 6).map { buildMember("Mon$it", PokemonType.NORMAL to null) }
        val result = generateTeam(chart, pool, locked, DEFAULT_CONSTRAINTS, Random(1))
        assertEquals(6, result.team.size)
        assertEquals("Mon0", result.team[0].speciesName)
    }

    // ---- constraint enforcement ----

    @Test
    fun `excludes legendaries and mythicals when legendaryMythicalSlots is 0`() {
        val constraints = DEFAULT_CONSTRAINTS.copy(legendaryMythicalSlots = 0)
        val result = generateTeam(chart, pool, emptyList(), constraints, Random(1))
        assertFalse(result.team.map { it.speciesName.lowercase() }.contains("mewtwo"))
    }

    @Test
    fun `respects the legendaryMythicalSlots cap`() {
        val constraints = DEFAULT_CONSTRAINTS.copy(legendaryMythicalSlots = 1)
        for (seed in 0..4) {
            val result = generateTeam(chart, pool, emptyList(), constraints, Random(seed))
            val count = result.team.count { m ->
                val entry = pool.find { it.displayName == m.speciesName || it.name == m.speciesName.lowercase() }
                entry?.isLegendary == true || entry?.isMythical == true
            }
            assertTrue("seed=$seed count=$count", count <= 1)
        }
    }

    // ---- buildEligiblePool ----

    @Test
    fun `buildEligiblePool keeps only final evolutions`() {
        val eligible = buildEligiblePool(pool, DEFAULT_CONSTRAINTS)
        assertTrue(eligible.all { it.isFinalEvolution })
    }

    @Test
    fun `buildEligiblePool excludes legendaries and mythicals when the constraint says so`() {
        val eligible = buildEligiblePool(pool, DEFAULT_CONSTRAINTS.copy(legendaryMythicalSlots = 0))
        assertFalse(eligible.any { it.isLegendary || it.isMythical })
    }

    // ---- regenerateSlot ----

    private fun sixMemberTeam() = listOf(
        buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING),
        buildMember("Gyarados", PokemonType.WATER to PokemonType.FLYING),
        buildMember("Mawile", PokemonType.STEEL to PokemonType.FAIRY),
        buildMember("Garchomp", PokemonType.DRAGON to PokemonType.GROUND),
        buildMember("Snorlax", PokemonType.NORMAL to null),
        buildMember("Sylveon", PokemonType.FAIRY to null),
    )

    @Test
    fun `regenerateSlot replaces a slot with a Pokemon from the pool`() {
        val team = sixMemberTeam()
        val newMember = regenerateSlot(chart, pool, team, 2, DEFAULT_CONSTRAINTS, Random(1))
        assertTrue(newMember.speciesName.isNotBlank())
    }

    @Test
    fun `regenerateSlot never picks a species already on one of the other slots`() {
        val team = sixMemberTeam()
        for (seed in 0..9) {
            val newMember = regenerateSlot(chart, pool, team, 5, DEFAULT_CONSTRAINTS, Random(seed))
            val otherNames = team.take(5).map { it.speciesName.lowercase() }
            assertFalse(otherNames.contains(newMember.speciesName.lowercase()))
        }
    }

    // A same-typed synthetic pool gives every candidate an identical base composite score, so
    // only computeScore's random noise breaks ties — the worst case for a comparator that must
    // stay consistent across repeated evaluations. Before the fix, sortedByDescending re-rolled
    // that noise on every comparison and threw "Comparison method violates its general contract!"
    // for a real fraction of seeds once the pool passed TimSort's insertion-sort threshold; this
    // pool (300 entries) reliably exceeds it. The assertion is that nothing above throws.
    private val largeTiedScorePool: List<PokemonEntry> = (1..300).map { i ->
        PokemonEntry(
            id = 20_000 + i, name = "mon$i", displayName = "Mon$i", speciesId = 20_000 + i, speciesName = "mon$i",
            types = PokemonType.NORMAL to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
            generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
        )
    }

    @Test
    fun `regenerateSlot does not crash on a large pool of tied composite scores`() {
        val team = sixMemberTeam()
        for (seed in 0..49) {
            regenerateSlot(chart, largeTiedScorePool, team, 5, DEFAULT_CONSTRAINTS, Random(seed))
        }
    }

    // ---- anchor composite score validation ----
    //
    // teamGenerator.test.ts runs this 5 times with real Math.random() and accepts 4/5 passes.
    // With an injectable Random, assert it holds for every one of a fixed set of seeds instead.

    private val diversePool: List<PokemonEntry> = listOf(
        PokemonEntry(260, "swampert", "Swampert", 260, "swampert", PokemonType.WATER to PokemonType.GROUND, false, false, true, 3, null, true),
        PokemonEntry(9, "blastoise", "Blastoise", 9, "blastoise", PokemonType.WATER to null, false, false, true, 1, null, true),
        PokemonEntry(730, "primarina", "Primarina", 730, "primarina", PokemonType.WATER to PokemonType.FAIRY, false, false, true, 7, null, true),
        PokemonEntry(503, "samurott", "Samurott", 503, "samurott", PokemonType.WATER to null, false, false, true, 5, null, true),
        PokemonEntry(131, "lapras", "Lapras", 131, "lapras", PokemonType.WATER to PokemonType.ICE, false, false, true, 1, null, true),
        PokemonEntry(6, "charizard", "Charizard", 6, "charizard", PokemonType.FIRE to PokemonType.FLYING, false, false, true, 1, null, true),
        PokemonEntry(445, "garchomp", "Garchomp", 445, "garchomp", PokemonType.DRAGON to PokemonType.GROUND, false, false, true, 4, null, true),
        PokemonEntry(303, "mawile", "Mawile", 303, "mawile", PokemonType.STEEL to PokemonType.FAIRY, false, false, true, 3, null, true),
        PokemonEntry(700, "sylveon", "Sylveon", 700, "sylveon", PokemonType.FAIRY to null, false, false, true, 6, null, true),
        PokemonEntry(94, "gengar", "Gengar", 94, "gengar", PokemonType.GHOST to PokemonType.POISON, false, false, true, 1, null, true),
        PokemonEntry(143, "snorlax", "Snorlax", 143, "snorlax", PokemonType.NORMAL to null, false, false, true, 1, null, true),
        PokemonEntry(65, "alakazam", "Alakazam", 65, "alakazam", PokemonType.PSYCHIC to null, false, false, true, 1, null, true),
        PokemonEntry(68, "machamp", "Machamp", 68, "machamp", PokemonType.FIGHTING to null, false, false, true, 1, null, true),
        PokemonEntry(462, "magnezone", "Magnezone", 462, "magnezone", PokemonType.ELECTRIC to PokemonType.STEEL, false, false, true, 4, null, true),
        PokemonEntry(3, "venusaur", "Venusaur", 3, "venusaur", PokemonType.GRASS to PokemonType.POISON, false, false, true, 1, null, true),
    )

    @Test
    fun `anchor composite score keeps additional Water types to at most 1, deterministically across seeds`() {
        val anchor = buildMember("Swampert", PokemonType.WATER to PokemonType.GROUND)
        for (seed in 0..9) {
            val result = generateTeam(chart, diversePool, listOf(anchor), DEFAULT_CONSTRAINTS, Random(seed))
            val generatedMembers = result.team.drop(1)
            val waterCount = generatedMembers.count { it.types.first == PokemonType.WATER || it.types.second == PokemonType.WATER }
            assertTrue("seed=$seed waterCount=$waterCount", waterCount <= 1)
        }
    }

    // ---- STARTER_FINALS — ported verbatim, asserted whole ----

    @Test
    fun `STARTER_FINALS matches the TypeScript data exactly`() {
        assertEquals(
            mapOf(
                1 to listOf("venusaur", "charizard", "blastoise"),
                2 to listOf("meganium", "typhlosion", "feraligatr"),
                3 to listOf("sceptile", "blaziken", "swampert"),
                4 to listOf("torterra", "infernape", "empoleon"),
                5 to listOf("serperior", "emboar", "samurott"),
                6 to listOf("chesnaught", "delphox", "greninja"),
                7 to listOf("decidueye", "incineroar", "primarina"),
                8 to listOf("rillaboom", "cinderace", "inteleon"),
                9 to listOf("meowscarada", "skeledirge", "quaquaval"),
            ),
            STARTER_FINALS,
        )
    }

    // ---- surpriseMe.test.ts — legendaries/mythicals merged counter ----

    private val extendedPool: List<PokemonEntry> = pool + listOf(
        PokemonEntry(151, "mew", "Mew", 151, "mew", PokemonType.PSYCHIC to null, false, true, true, 1, null, true),
        PokemonEntry(249, "lugia", "Lugia", 249, "lugia", PokemonType.PSYCHIC to PokemonType.FLYING, true, false, true, 2, null, true),
        PokemonEntry(250, "ho-oh", "Ho-Oh", 250, "ho-oh", PokemonType.FIRE to PokemonType.FLYING, true, false, true, 2, null, true),
        PokemonEntry(386, "deoxys", "Deoxys", 386, "deoxys", PokemonType.PSYCHIC to null, false, true, true, 3, null, true),
    )

    private fun legendaryMythicalCount(team: List<com.marcogn.coverdex.domain.model.TeamMember>) = team.count { m ->
        val entry = extendedPool.find { it.displayName == m.speciesName || it.name == m.speciesName.lowercase() }
        entry?.isLegendary == true || entry?.isMythical == true
    }

    @Test
    fun `legendaryMythicalSlots = 2 yields exactly 2 legendary-or-mythical team members`() {
        val constraints = DEFAULT_CONSTRAINTS.copy(legendaryMythicalSlots = 2)
        for (seed in 0..4) {
            val result = generateTeam(chart, extendedPool, emptyList(), constraints, Random(seed))
            assertEquals("seed=$seed", 2, legendaryMythicalCount(result.team))
        }
    }

    @Test
    fun `legendaryMythicalSlots = 0 yields no legendary or mythical team member`() {
        val constraints = DEFAULT_CONSTRAINTS.copy(legendaryMythicalSlots = 0)
        val result = generateTeam(chart, extendedPool, emptyList(), constraints, Random(1))
        assertEquals(0, legendaryMythicalCount(result.team))
    }

    @Test
    fun `re-randomizing the last slot can return a different Pokemon across repeated calls`() {
        val constraints = DEFAULT_CONSTRAINTS.copy(legendaryMythicalSlots = 1)
        val result = generateTeam(chart, extendedPool, emptyList(), constraints, Random(1))
        assertEquals(6, result.team.size)

        val results = mutableSetOf<String>()
        for (seed in 0 until 20) {
            val newMember = regenerateSlot(chart, extendedPool, result.team, 5, constraints, Random(seed))
            assertTrue(newMember.speciesName.isNotBlank())
            results.add(newMember.speciesName)
        }
        assertTrue(results.size >= 2)
    }

    @Test
    fun `re-randomizing slot 0 leaves every other slot unchanged`() {
        val result = generateTeam(chart, extendedPool, emptyList(), DEFAULT_CONSTRAINTS, Random(1))
        assertEquals(6, result.team.size)

        val originalTeam = result.team
        val newMember = regenerateSlot(chart, extendedPool, result.team, 0, DEFAULT_CONSTRAINTS, Random(2))

        val newTeam = listOf(newMember) + originalTeam.drop(1)
        for (i in 1 until 6) {
            assertEquals(originalTeam[i].speciesName, newTeam[i].speciesName)
            assertEquals(originalTeam[i].types, newTeam[i].types)
        }
    }
}
