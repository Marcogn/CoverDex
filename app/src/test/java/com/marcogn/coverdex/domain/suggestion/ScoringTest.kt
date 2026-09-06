package com.marcogn.coverdex.domain.suggestion

import com.marcogn.coverdex.domain.buildMember
import com.marcogn.coverdex.domain.mockTypeChart
import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `weaknesses` gained an [ability] parameter as a deliberate spec change — see
 * `docs/post-migration-review.md`, finding 6, and `docs/implementation-decisions.md`, "Post-
 * migration review". Before this, `computeCompositeScore` (and the suggestion/generator scoring
 * built on it) could report a candidate as "aggravating" a teammate's weakness the coverage grid
 * on the very same screen already excluded via that teammate's ability (e.g. Levitate's Ground
 * immunity), or penalize a candidate for a weakness its own ability removes.
 */
class ScoringTest {

    private val chart = mockTypeChart()

    @Test
    fun `weaknesses without an ability includes Ground for an Electric type`() {
        assertTrue(weaknesses(chart, PokemonType.ELECTRIC to null).contains(PokemonType.GROUND))
    }

    @Test
    fun `weaknesses with levitate excludes the Ground immunity`() {
        val withoutAbility = weaknesses(chart, PokemonType.ELECTRIC to null)
        val withLevitate = weaknesses(chart, PokemonType.ELECTRIC to null, "levitate")
        assertTrue(withoutAbility.contains(PokemonType.GROUND))
        assertFalse(withLevitate.contains(PokemonType.GROUND))
    }

    @Test
    fun `weaknesses honours a held item too, not just the ability (Phase 7 item threading)`() {
        val withoutItem = weaknesses(chart, PokemonType.ELECTRIC to null)
        val withAirBalloon = weaknesses(chart, PokemonType.ELECTRIC to null, item = "air-balloon")
        assertTrue(withoutItem.contains(PokemonType.GROUND))
        assertFalse(withAirBalloon.contains(PokemonType.GROUND))
    }

    @Test
    fun `weaknesses with an unknown ability behaves exactly like no ability`() {
        val withoutAbility = weaknesses(chart, PokemonType.ELECTRIC to null)
        val withUnknownAbility = weaknesses(chart, PokemonType.ELECTRIC to null, "not-a-real-ability")
        assertEquals(withoutAbility, withUnknownAbility)
    }

    @Test
    fun `computeCompositeScore does not count a weakness the candidate's own ability immunizes away`() {
        val teammate = buildMember("Teammate", PokemonType.NORMAL to null)
        val context = teamScoringContext(chart, listOf(teammate))

        val candidateWithoutAbility = buildMember("Pikachu", PokemonType.ELECTRIC to null)
        val candidateWithLevitate = buildMember("Pikachu", PokemonType.ELECTRIC to null, ability = "levitate")

        val withoutAbility = computeCompositeScore(chart, candidateWithoutAbility, context, emptySet())
        val withLevitate = computeCompositeScore(chart, candidateWithLevitate, context, emptySet())

        assertTrue(withoutAbility.newWeaknesses.contains(PokemonType.GROUND))
        assertFalse(withLevitate.newWeaknesses.contains(PokemonType.GROUND))
        assertTrue(withLevitate.compositeScore > withoutAbility.compositeScore)
    }

    @Test
    fun `teamScoringContext does not attribute a weakness to a teammate whose ability immunizes it`() {
        val teammateWithoutAbility = buildMember("Zapdos", PokemonType.ELECTRIC to null)
        val teammateWithLevitate = buildMember("Zapdos", PokemonType.ELECTRIC to null, ability = "levitate")

        val contextWithoutAbility = teamScoringContext(chart, listOf(teammateWithoutAbility))
        val contextWithLevitate = teamScoringContext(chart, listOf(teammateWithLevitate))

        assertTrue(contextWithoutAbility.otherWeaknessMap.containsKey(PokemonType.GROUND))
        assertFalse(contextWithLevitate.otherWeaknessMap.containsKey(PokemonType.GROUND))

        // A second Electric-type candidate shares that same single Ground weakness (weaknesses()
        // for a plain Electric type is exactly [GROUND] in this fixture chart). Against the
        // ability-less teammate, that weakness is aggravated (a real shared exposure); against the
        // Levitate teammate, whose own Ground weakness the ability already removed, it is only new
        // — aggravating costs 1.0, a brand-new weakness only 0.5 (NEW_WEAKNESS_PENALTY /
        // AGGRAVATED_WEAKNESS_PENALTY), so the two scores must differ. Before the finding 6 fix,
        // both contexts would have counted the teammate's weakness and wrongly agreed.
        val candidate = buildMember("Raichu", PokemonType.ELECTRIC to null)
        val aggravated = computeCompositeScore(chart, candidate, contextWithoutAbility, emptySet())
        val newOnly = computeCompositeScore(chart, candidate, contextWithLevitate, emptySet())

        assertTrue(aggravated.aggravatedWeaknesses.contains(PokemonType.GROUND))
        assertTrue(newOnly.newWeaknesses.contains(PokemonType.GROUND))
        assertTrue(newOnly.compositeScore > aggravated.compositeScore)
    }
}
