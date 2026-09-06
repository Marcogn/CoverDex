package com.marcogn.coverdex.domain.suggestion

import com.marcogn.coverdex.domain.buildMember
import com.marcogn.coverdex.domain.mockPokemonList
import com.marcogn.coverdex.domain.mockTypeChart
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Ports `legacy-web/src/hooks/__tests__/{suggestionRanking,addToTeam,useSuggestions,
 * compositeScoring}.test.ts` — every case that exercises `computeSuggestions` itself, with the
 * same expected values, either as exact numbers where the TS test asserted exact numbers or as
 * the same invariants where the TS test asserted invariants (most of them do: sort order,
 * membership, "not to contain", "greater than"). `useSuggestionsHook.test.ts` is not ported — it
 * tests React's `useMemo` re-render/memoisation behaviour, which has no Kotlin equivalent;
 * `computeSuggestions` itself is a pure function with no memoisation to test, and the ViewModel
 * layer that wraps it (`ui/team/analysis/AnalysisViewModel.kt`) is covered separately.
 * `addToTeam.test.ts`'s "Random mode filtering" tests are also not ported: they exercise
 * scratch `Math.random()`-based picking logic written inline in that test file, not any exported
 * function from `suggestionEngine.ts`.
 */
class SuggestionEngineTest {

    private val chart = mockTypeChart()
    private val pool = mockPokemonList()

    private fun labelsOf(suggestions: List<Suggestion>) = suggestions.map { it.candidateLabel }

    // ---- addition mode (useSuggestions.test.ts) ----

    @Test
    fun `team of 1 returns add suggestions only, sorted by gain descending`() {
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val suggestions = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.kind == Suggestion.Kind.ADD })
        for (i in 1 until suggestions.size) {
            assertTrue(suggestions[i - 1].gain >= suggestions[i].gain)
        }
    }

    @Test
    fun `mid-evolutions are never suggested`() {
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val suggestions = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertFalse(labelsOf(suggestions).contains("Gastly"))
    }

    @Test
    fun `custom Pokemon appear only when includeCustoms is true`() {
        val tinyPool = pool.take(2)
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val customs = listOf(buildMember("MyCustomMon", PokemonType.ICE to PokemonType.DRAGON))

        val without = computeSuggestions(chart, team, tinyPool, customs, SuggestionOptions(includeCustoms = false))
        val withCustoms = computeSuggestions(chart, team, tinyPool, customs, SuggestionOptions(includeCustoms = true))

        assertFalse(labelsOf(without).contains("MyCustomMon"))
        assertTrue(labelsOf(withCustoms).contains("MyCustomMon"))
    }

    @Test
    fun `legendary candidates are included unconditionally by default`() {
        val restrictedPool = listOf(
            pool.first { it.name == "mewtwo" },
            pool.first { it.name == "snorlax" },
        )
        val team = listOf(buildMember("Slot", PokemonType.NORMAL to null))
        val suggestions = computeSuggestions(chart, team, restrictedPool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertTrue(labelsOf(suggestions).contains("Mewtwo"))
    }

    @Test
    fun `mythical candidates are included unconditionally`() {
        val mythical = PokemonEntry(
            id = 151, name = "mew", displayName = "Mew", speciesId = 151, speciesName = "mew",
            types = PokemonType.PSYCHIC to null, isLegendary = false, isMythical = true, isFinalEvolution = true,
            generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
        )
        val restrictedPool = listOf(mythical, pool.first { it.name == "snorlax" })
        val team = listOf(buildMember("Slot", PokemonType.NORMAL to null))
        val suggestions = computeSuggestions(chart, team, restrictedPool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertTrue(labelsOf(suggestions).contains("Mew"))
    }

    // ---- replacement mode (useSuggestions.test.ts) ----

    private fun fullTeam() = listOf(
        buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING),
        buildMember("Gyarados", PokemonType.WATER to PokemonType.FLYING),
        buildMember("Mawile", PokemonType.STEEL to PokemonType.FAIRY),
        buildMember("Garchomp", PokemonType.DRAGON to PokemonType.GROUND),
        // Overlaps Charizard completely in pure types -> lowest unique contribution.
        buildMember("Moltres", PokemonType.FIRE to PokemonType.FLYING),
        buildMember("Sylveon", PokemonType.FAIRY to null),
    )

    @Test
    fun `team of 6 returns replacement suggestions with a replacesMemberId`() {
        val suggestions = computeSuggestions(chart, fullTeam(), pool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.kind == Suggestion.Kind.REPLACE })
        assertTrue(suggestions.all { it.replacesMemberId != null })
    }

    @Test
    fun `weakest-link identification picks the fully-overlapping slot`() {
        val suggestions = computeSuggestions(chart, fullTeam(), pool, emptyList(), SuggestionOptions(includeCustoms = false))
        val replacedNames = suggestions.mapNotNull { it.replacesName }.toSet()
        assertTrue(replacedNames.any { it == "Moltres" || it == "Charizard" })
    }

    @Test
    fun `no duplicate suggestions across slots, unique by species`() {
        val suggestions = computeSuggestions(chart, fullTeam(), pool, emptyList(), SuggestionOptions(includeCustoms = false))
        val labels = labelsOf(suggestions).map { it.lowercase() }
        assertEquals(labels.toSet().size, labels.size)
    }

    // ---- branched evolutions ----

    private val branchedPool = listOf(
        PokemonEntry(9001, "splitbase", "SplitBase", 9001, "splitbase", PokemonType.NORMAL to null, false, false, false, 1, null, true),
        PokemonEntry(9002, "splitfinal-water", "SplitFinal-Water", 9001, "splitfinal-water", PokemonType.WATER to null, false, false, true, 1, null, true),
        PokemonEntry(9003, "splitfinal-electric", "SplitFinal-Electric", 9001, "splitfinal-electric", PokemonType.ELECTRIC to null, false, false, true, 1, null, true),
    )

    @Test
    fun `both final-evolution branches appear as separate candidates, mid-evolution excluded`() {
        val team = listOf(buildMember("Snorlax", PokemonType.NORMAL to null))
        val suggestions = computeSuggestions(chart, team, branchedPool, emptyList(), SuggestionOptions(includeCustoms = false))
        val names = labelsOf(suggestions)
        assertTrue(names.contains("SplitFinal-Water"))
        assertTrue(names.contains("SplitFinal-Electric"))
        assertFalse(names.contains("SplitBase"))
    }

    @Test
    fun `when Water is already covered, the Electric branch ranks ahead of the Water branch`() {
        val team = listOf(buildMember("AquaBeast", PokemonType.WATER to PokemonType.GROUND))
        val suggestions = computeSuggestions(chart, team, branchedPool, emptyList(), SuggestionOptions(includeCustoms = false))
        val idxElectric = labelsOf(suggestions).indexOf("SplitFinal-Electric")
        val idxWater = labelsOf(suggestions).indexOf("SplitFinal-Water")
        assertTrue(idxElectric >= 0)
        if (idxWater >= 0) assertTrue(idxElectric < idxWater)
    }

    // ---- alternate forms ----

    private val formsPool = listOf(
        PokemonEntry(7001, "rotom", "Rotom", 479, "rotom", PokemonType.ELECTRIC to PokemonType.GHOST, false, false, true, 4, null, true),
        PokemonEntry(7002, "rotom-heat", "Rotom-Heat", 479, "rotom", PokemonType.ELECTRIC to PokemonType.FIRE, false, false, true, 4, null, false),
    )

    @Test
    fun `alternate forms are distinct candidates, never deduplicated against each other`() {
        val team = listOf(buildMember("Snorlax", PokemonType.NORMAL to null))
        val suggestions = computeSuggestions(chart, team, formsPool, emptyList(), SuggestionOptions(includeCustoms = false))
        val names = labelsOf(suggestions)
        assertTrue(names.contains("Rotom"))
        assertTrue(names.contains("Rotom-Heat"))
    }

    @Test
    fun `suggestion label carries the full form name, not collapsed to the species name`() {
        val team = listOf(buildMember("Snorlax", PokemonType.NORMAL to null))
        val suggestions = computeSuggestions(chart, team, formsPool, emptyList(), SuggestionOptions(includeCustoms = false))
        val heat = suggestions.first { it.candidateLabel == "Rotom-Heat" }
        assertNotNull(heat)
        assertFalse(heat.candidateLabel == "Rotom")
    }

    // ---- custom Pokémon evaluated by types only ----

    @Test
    fun `custom Pokemon gain is computed from types, never from its own moves`() {
        val team = listOf(buildMember("Snorlax", PokemonType.NORMAL to null))
        val custom = buildMember("MyDragoSteel", PokemonType.DRAGON to PokemonType.STEEL).copy(
            moves = listOf(
                PokemonMove("m1", "flamethrower", PokemonType.FIRE, 90, DamageClass.SPECIAL, isCustom = false),
                PokemonMove("m2", "ice-beam", PokemonType.ICE, 90, DamageClass.SPECIAL, isCustom = false),
                null,
                null,
            ),
        )
        val suggestions = computeSuggestions(chart, team, pool, listOf(custom), SuggestionOptions(includeCustoms = true))
        val found = suggestions.first { it.candidateLabel == "MyDragoSteel" }

        // Steel -> ice, fairy (2x); Dragon -> dragon. None of Fire/Ice's move-type coverage
        // (which would additionally reach grass, bug, steel...) may leak in.
        assertTrue(found.newlyCovered.contains(PokemonType.ICE))
        assertTrue(found.newlyCovered.contains(PokemonType.FAIRY))
        assertFalse(found.newlyCovered.contains(PokemonType.GRASS))
    }

    // ---- ranking (suggestionRanking.test.ts) ----

    @Test
    fun `addition mode candidates are sorted by compositeScore descending`() {
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val suggestions = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertTrue(suggestions.size > 1)
        for (i in 1 until suggestions.size) {
            assertTrue(suggestions[i - 1].compositeScore >= suggestions[i].compositeScore)
        }
    }

    @Test
    fun `replacement mode candidates are sorted by compositeScore descending`() {
        val suggestions = computeSuggestions(chart, fullTeam(), pool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertTrue(suggestions.size > 1)
        for (i in 1 until suggestions.size) {
            assertTrue(suggestions[i - 1].compositeScore >= suggestions[i].compositeScore)
        }
    }

    @Test
    fun `on a compositeScore tie, the higher baseStatTotal ranks first, ahead of catalogue id`() {
        // A hand-built pool, not mockPokemonList() — engineered so both candidates score
        // identically (docs/plan/phase-7-accuracy-and-customization.md §5.1's own regression:
        // a solid team's remaining candidates tie on compositeScore and used to fall back
        // straight to ascending id, surfacing Raticate/Persian/Kangaskhan regardless of how
        // strong the alternative actually was).
        fun normalCandidate(id: Int, bst: Int) = PokemonEntry(
            id = id, name = "normal$id", displayName = "Normal$id", speciesId = id, speciesName = "normal$id",
            types = PokemonType.NORMAL to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
            generationIntroduced = 1, defaultAbility = null, isDefaultForm = true, baseStatTotal = bst,
        )
        // Lower id, lower BST.
        val weak = normalCandidate(id = 1, bst = 300)
        // Higher id, higher BST — must outrank `weak` despite the higher id.
        val strong = normalCandidate(id = 2, bst = 500)
        val handBuiltPool = listOf(weak, strong)
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))

        val suggestions = computeSuggestions(chart, team, handBuiltPool, emptyList(), SuggestionOptions(includeCustoms = false))

        assertEquals(2, suggestions.size)
        assertEquals(suggestions[0].compositeScore, suggestions[1].compositeScore, 0.0)
        assertEquals("Normal2", suggestions[0].candidateLabel)
        assertEquals(500, suggestions[0].baseStatTotal)
        assertEquals("Normal1", suggestions[1].candidateLabel)
    }

    @Test
    fun `on a compositeScore AND baseStatTotal tie, ascending catalogue id still decides`() {
        fun normalCandidate(id: Int) = PokemonEntry(
            id = id, name = "normal$id", displayName = "Normal$id", speciesId = id, speciesName = "normal$id",
            types = PokemonType.NORMAL to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
            generationIntroduced = 1, defaultAbility = null, isDefaultForm = true, baseStatTotal = 300,
        )
        val handBuiltPool = listOf(normalCandidate(id = 5), normalCandidate(id = 3))
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))

        val suggestions = computeSuggestions(chart, team, handBuiltPool, emptyList(), SuggestionOptions(includeCustoms = false))

        assertEquals(2, suggestions.size)
        assertEquals(suggestions[0].compositeScore, suggestions[1].compositeScore, 0.0)
        assertEquals(suggestions[0].baseStatTotal, suggestions[1].baseStatTotal)
        assertEquals("Normal3", suggestions[0].candidateLabel)
        assertEquals("Normal5", suggestions[1].candidateLabel)
    }

    // ---- composite scoring (compositeScoring.test.ts) ----

    @Test
    fun `compositeScore always equals gain minus the two weighted penalties`() {
        val team = listOf(
            buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING),
            buildMember("Gyarados", PokemonType.WATER to PokemonType.FLYING),
        )
        val suggestions = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))
        for (s in suggestions) {
            val expected = s.gain - NEW_WEAKNESS_PENALTY * s.newWeaknesses.size - AGGRAVATED_WEAKNESS_PENALTY * s.aggravatedWeaknesses.size
            assertTrue(abs(s.compositeScore - expected) < 1e-9)
        }
    }

    @Test
    fun `a candidate with no new weaknesses outscores an equal-gain candidate with two`() {
        val team = listOf(buildMember("Snorlax", PokemonType.NORMAL to null))
        val suggestions = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))
        val noWeakness = suggestions.find { it.newWeaknesses.isEmpty() && it.aggravatedWeaknesses.isEmpty() }
        val withWeakness = suggestions.find { it.newWeaknesses.size >= 2 }
        if (noWeakness != null && withWeakness != null && noWeakness.gain == withWeakness.gain) {
            assertTrue(noWeakness.compositeScore > withWeakness.compositeScore)
        }
    }

    @Test
    fun `computeSuggestions returns every eligible candidate, not capped at 5 or 10`() {
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val suggestions = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))
        val finalEvos = pool.count { it.isFinalEvolution && it.speciesName != "pikachu" }
        assertEquals(finalEvos, suggestions.size)
    }

    @Test
    fun `every replacement suggestion names a member to replace`() {
        val suggestions = computeSuggestions(chart, fullTeam(), pool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertTrue(suggestions.isNotEmpty())
        for (s in suggestions) {
            assertEquals(Suggestion.Kind.REPLACE, s.kind)
            assertNotNull(s.replacesMemberId)
            assertNotNull(s.replacesName)
        }
    }

    // ---- generation filter — the one intentional deviation from suggestionEngine.ts ----
    //
    // suggestionEngine.ts filters by hardcoded id ranges (GEN_RANGES); this port filters by the
    // real generationIntroduced instead (docs/plan/phase-4-suggestions-and-generator.md §2).
    // Spectraform (id 9301, deliberately > 10000-style in the fixtures) is given
    // generationIntroduced = 1: under the old id-range scheme it would fall outside [1, 151] and
    // never appear as a Generation I suggestion; under the real generation it correctly does.

    @Test
    fun `generation filter uses the real generationIntroduced, not an id range`() {
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val gen1 = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false, generation = 1))
        for (s in gen1) {
            val entry = pool.find { it.displayName == s.candidateLabel }
            assertEquals(1, entry?.generationIntroduced)
        }
        // The whole point of the deviation: a high-id alt-form entry with a real gen1 species
        // still shows up under the Generation I filter.
        assertTrue(labelsOf(gen1).contains("Spectraform"))

        val gen4 = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false, generation = 4))
        for (s in gen4) {
            val entry = pool.find { it.displayName == s.candidateLabel }
            assertEquals(4, entry?.generationIntroduced)
        }
        assertEquals(listOf("Garchomp"), labelsOf(gen4))
    }

    @Test
    fun `generation null means all generations, same result as omitting the filter`() {
        val team = listOf(buildMember("Pikachu", PokemonType.ELECTRIC to null))
        val withNull = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false, generation = null))
        val omitted = computeSuggestions(chart, team, pool, emptyList(), SuggestionOptions(includeCustoms = false))
        assertEquals(labelsOf(omitted), labelsOf(withNull))
    }
}
