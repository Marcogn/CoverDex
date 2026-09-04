package com.marcogn.coverdex.domain.coverage

import com.marcogn.coverdex.domain.buildMember
import com.marcogn.coverdex.domain.mockTypeChart
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported case by case from `legacy-web/src/utils/__tests__/coverageEngine.test.ts` — same
 * expected values, not re-derived (`docs/plan/phase-3-analysis.md`, "Tests"). Group headers match
 * the TS file's `describe` blocks so the two can be read side by side.
 */
class CoverageEngineTest {

    private val chart = mockTypeChart()

    // --- defensiveMultiplier ---

    @Test
    fun `single-type - Electric vs Water is super-effective (2x)`() {
        assertEquals(2.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.WATER to null), 0.0)
    }

    @Test
    fun `single-type - Normal vs Ghost is immune (0x)`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.NORMAL, PokemonType.GHOST to null), 0.0)
    }

    @Test
    fun `dual-type defense - Water Ground vs Electric is immune (Ground cancels)`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.WATER to PokemonType.GROUND), 0.0)
    }

    @Test
    fun `dual-type defense - Fire Flying vs Rock is 4x (both weak)`() {
        assertEquals(4.0, defensiveMultiplier(chart, PokemonType.ROCK, PokemonType.FIRE to PokemonType.FLYING), 0.0)
    }

    @Test
    fun `dual-type defense - Steel Flying vs Poison is immune (Steel immunity)`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.POISON, PokemonType.STEEL to PokemonType.FLYING), 0.0)
    }

    @Test
    fun `neutral by default for unrelated matchups`() {
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.NORMAL, PokemonType.WATER to null), 0.0)
    }

    // --- offensiveCoverageForMember ---

    @Test
    fun `offensiveCoverageForMember uses types when no moves entered`() {
        val m = buildMember("Pikachu", PokemonType.ELECTRIC to null)
        val cov = offensiveCoverageForMember(chart, m, false)
        assertTrue(cov.contains(PokemonType.WATER))
        assertTrue(cov.contains(PokemonType.FLYING))
        assertFalse(cov.contains(PokemonType.GROUND))
    }

    @Test
    fun `offensiveCoverageForMember uses move types when useMoves is true`() {
        val m = buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING, listOf(PokemonType.FIRE, PokemonType.GROUND))
        val cov = offensiveCoverageForMember(chart, m, true)
        // Ground hits Electric super-effectively, types alone would not.
        assertTrue(cov.contains(PokemonType.ELECTRIC))
    }

    @Test
    fun `offensiveCoverageForMember ignores status moves and zero-power moves`() {
        val leechSeed = PokemonMove(
            id = "x", name = "leech-seed", type = PokemonType.GRASS,
            power = null, damageClass = DamageClass.STATUS, isCustom = false,
        )
        val m = buildMember("Snorlax", PokemonType.NORMAL to null).copy(moves = listOf(leechSeed, null, null, null))
        assertFalse(memberHasMoves(m))
        val cov = offensiveCoverageForMember(chart, m, true)
        assertEquals(0, cov.size)
    }

    // --- analyseTeam ---

    @Test
    fun `analyseTeam team union covers more than each member alone`() {
        val m1 = buildMember("Pikachu", PokemonType.ELECTRIC to null)
        val m2 = buildMember("Charizard", PokemonType.FIRE to null)
        val a1 = analyseTeam(chart, listOf(m1))
        val a2 = analyseTeam(chart, listOf(m2))
        val aBoth = analyseTeam(chart, listOf(m1, m2))
        assertTrue(aBoth.unionCovered.size > a1.unionCovered.size)
        assertTrue(aBoth.unionCovered.size > a2.unionCovered.size)
    }

    @Test
    fun `analyseTeam reports uncovered types when team has narrow coverage`() {
        val m = buildMember("Snorlax", PokemonType.NORMAL to null)
        val a = analyseTeam(chart, listOf(m))
        // Normal hits no types for >=2x -> fully uncovered.
        assertEquals(PokemonType.entries.size, a.uncovered.size)
    }

    // --- unique contribution / gain ---

    private fun uniqueContribution(members: List<TeamMember>): Map<String, Int> {
        val a = analyseTeam(chart, members)
        return members.associate { m ->
            val mine = a.perMemberCovered[m.id] ?: emptySet()
            val unique = mine.count { t -> members.none { other -> other.id != m.id && a.perMemberCovered[other.id]?.contains(t) == true } }
            m.id to unique
        }
    }

    @Test
    fun `member whose types overlap entirely has contribution 0`() {
        val a = buildMember("Char-A", PokemonType.FIRE to null)
        val b = buildMember("Char-B", PokemonType.FIRE to null)
        val u = uniqueContribution(listOf(a, b))
        assertEquals(0, u[a.id])
        assertEquals(0, u[b.id])
    }

    @Test
    fun `replacing a 0-contribution member with a better-typed one yields gain greater than 0`() {
        val a = buildMember("Char-A", PokemonType.FIRE to null)
        val b = buildMember("Char-B", PokemonType.FIRE to null)
        val teamBefore = analyseTeam(chart, listOf(a, b))
        val c = buildMember("Garchomp", PokemonType.DRAGON to PokemonType.GROUND)
        val teamAfter = analyseTeam(chart, listOf(a, c))
        val gain = teamAfter.unionCovered.size - teamBefore.unionCovered.size
        assertTrue(gain > 0)
    }

    // --- edge cases ---

    @Test
    fun `team of 1 Pokemon analyses without errors`() {
        val m = buildMember("Pikachu", PokemonType.ELECTRIC to null)
        val a = analyseTeam(chart, listOf(m))
        assertEquals(1, a.perMemberCovered.size)
        assertTrue(a.uncovered.size < PokemonType.entries.size)
    }

    @Test
    fun `all 18 types covered team never crashes and returns a consistent structure`() {
        val team = listOf(
            buildMember("A", PokemonType.FIGHTING to null, listOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.ICE)),
            buildMember("B", PokemonType.GROUND to PokemonType.WATER, listOf(PokemonType.GROUND, PokemonType.WATER)),
            buildMember("C", PokemonType.GHOST to PokemonType.PSYCHIC, listOf(PokemonType.GHOST, PokemonType.PSYCHIC)),
            buildMember("D", PokemonType.FAIRY to PokemonType.STEEL, listOf(PokemonType.FAIRY, PokemonType.STEEL)),
            buildMember("E", PokemonType.FIRE to PokemonType.FLYING, listOf(PokemonType.FIRE, PokemonType.FLYING, PokemonType.ELECTRIC)),
            buildMember("F", PokemonType.DRAGON to PokemonType.DARK, listOf(PokemonType.DRAGON, PokemonType.DARK, PokemonType.BUG)),
        )
        val a = analyseTeam(chart, team)
        // It's OK if some types remain uncovered (Normal hits nothing SE), but this must never
        // crash and must return a consistent structure.
        assertTrue(a.uncovered.size <= PokemonType.entries.size)
    }

    // --- defensiveProfile & sharedWeaknesses ---

    @Test
    fun `defensiveProfile classifies weaknesses resistances and immunities`() {
        val p = defensiveProfile(chart, PokemonType.FIRE to PokemonType.FLYING)
        assertTrue(p.weaknesses.contains(PokemonType.ROCK)) // 4x
        assertTrue(p.weaknesses.contains(PokemonType.ELECTRIC))
        assertTrue(p.weaknesses.contains(PokemonType.WATER))
        assertTrue(p.immunities.contains(PokemonType.GROUND))
    }

    @Test
    fun `sharedWeaknesses lists types that hit 2 or more members super-effectively`() {
        val team = listOf(
            buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING),
            buildMember("Pidgey", PokemonType.NORMAL to PokemonType.FLYING),
        )
        val shared = sharedWeaknesses(chart, team)
        assertTrue(shared.contains(PokemonType.ELECTRIC))
        assertTrue(shared.contains(PokemonType.ROCK))
    }

    // --- type overrides ---

    @Test
    fun `overriding types changes the offensive coverage`() {
        // Original Water/Flying -> covers fire, ground, rock (water) + fighting, bug, grass (flying)
        val original = buildMember("Slot", PokemonType.WATER to PokemonType.FLYING)
        val overridden = buildMember("Slot", PokemonType.FIRE to PokemonType.GROUND, id = original.id)
        val covOriginal = offensiveCoverageForMember(chart, original, false)
        val covOverridden = offensiveCoverageForMember(chart, overridden, false)

        // Fire/Ground covers electric (Ground 2x electric); Water/Flying does not.
        assertTrue(covOverridden.contains(PokemonType.ELECTRIC))
        assertFalse(covOriginal.contains(PokemonType.ELECTRIC))
        // Water/Flying covers fighting (Flying 2x fighting); Fire/Ground does not.
        assertTrue(covOriginal.contains(PokemonType.FIGHTING))
        assertFalse(covOverridden.contains(PokemonType.FIGHTING))
    }

    @Test
    fun `overriding one type removes that original type from coverage output`() {
        // Start as Water/Flying, override type 1 to Grass -> Grass/Flying.
        val member = buildMember("Slot", PokemonType.GRASS to PokemonType.FLYING)
        val cov = offensiveCoverageForMember(chart, member, false)
        // Original (Water) hit Fire SE; with Grass replacing Water, Fire should not appear anymore
        // (Grass is 0.5x vs Fire).
        assertFalse(cov.contains(PokemonType.FIRE))
        // Grass-typical coverage should be present (rock via grass 2x).
        assertTrue(cov.contains(PokemonType.ROCK))
    }

    // --- custom Pokemon type-only evaluation ---

    @Test
    fun `coverage of a candidate uses types only, even when moves are present`() {
        val fireBlast = PokemonMove(id = "m1", name = "fire-blast", type = PokemonType.FIRE, power = 110, damageClass = DamageClass.SPECIAL, isCustom = false)
        val earthquake = PokemonMove(id = "m2", name = "earthquake", type = PokemonType.GROUND, power = 100, damageClass = DamageClass.PHYSICAL, isCustom = false)
        val custom = buildMember("CustomMon", PokemonType.STEEL to PokemonType.FAIRY)
            .copy(moves = listOf(fireBlast, earthquake, null, null))

        // Candidates in the suggestion engine are always evaluated with useMoves=false, so Fire
        // and Ground from the saved moves must not appear.
        val cov = offensiveCoverageForMember(chart, custom, false)
        assertFalse(cov.contains(PokemonType.FIRE))
        assertFalse(cov.contains(PokemonType.GROUND))
        // But Steel-typical coverage (ice, rock, fairy) and Fairy-typical coverage (fighting,
        // dragon, dark) should be present.
        assertTrue(cov.contains(PokemonType.ICE))
        assertTrue(cov.contains(PokemonType.ROCK))
        assertTrue(cov.contains(PokemonType.DRAGON))
        assertTrue(cov.contains(PokemonType.DARK))
    }

    // --- ability effects ---

    @Test
    fun `volt-absorb zeroes out Electric multiplier`() {
        // Water/Flying is normally 4x weak to Electric (2*2)
        assertEquals(4.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.WATER to PokemonType.FLYING), 0.0)
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.WATER to PokemonType.FLYING, "volt-absorb"), 0.0)
    }

    @Test
    fun `lightning-rod zeroes out Electric multiplier`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.WATER to null, "lightning-rod"), 0.0)
    }

    @Test
    fun `water-absorb zeroes out Water multiplier`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.WATER, PokemonType.FIRE to null, "water-absorb"), 0.0)
    }

    @Test
    fun `flash-fire zeroes out Fire multiplier`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, "flash-fire"), 0.0)
    }

    @Test
    fun `levitate zeroes out Ground multiplier`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.GHOST to PokemonType.POISON, "levitate"), 0.0)
    }

    @Test
    fun `sap-sipper zeroes out Grass multiplier`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.GRASS, PokemonType.WATER to null, "sap-sipper"), 0.0)
    }

    @Test
    fun `thick-fat halves Fire damage`() {
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, "thick-fat"), 0.0)
    }

    @Test
    fun `thick-fat halves Ice damage`() {
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.ICE, PokemonType.NORMAL to null, "thick-fat"), 0.0)
    }

    @Test
    fun `thick-fat stacks with type resistances`() {
        assertEquals(0.25, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.WATER to null, "thick-fat"), 0.0)
    }

    @Test
    fun `fluffy doubles Fire damage defensively`() {
        assertEquals(2.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, "fluffy"), 0.0)
    }

    @Test
    fun `fluffy stacks with type weakness`() {
        assertEquals(4.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, "fluffy"), 0.0)
    }

    @Test
    fun `wonder-guard does not alter any multiplier (badge-only)`() {
        assertEquals(
            defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GHOST to null),
            defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GHOST to null, "wonder-guard"),
            0.0,
        )
        assertEquals(
            defensiveMultiplier(chart, PokemonType.DARK, PokemonType.GHOST to null),
            defensiveMultiplier(chart, PokemonType.DARK, PokemonType.GHOST to null, "wonder-guard"),
            0.0,
        )
    }

    @Test
    fun `immunity overrides even when the type chart already shows 0 (motor-drive vs ground)`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.GROUND to null, "motor-drive"), 0.0)
    }

    @Test
    fun `unknown ability has no effect`() {
        assertEquals(2.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, "some-random-ability"), 0.0)
    }

    @Test
    fun `sharedWeaknesses accounts for abilities`() {
        val team = listOf(
            buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING, ability = "flash-fire"),
            buildMember("Pidgey", PokemonType.NORMAL to PokemonType.FLYING),
        )
        val shared = sharedWeaknesses(chart, team)
        assertTrue(shared.contains(PokemonType.ROCK))
        assertTrue(shared.contains(PokemonType.ELECTRIC))
    }

    @Test
    fun `defensiveProfile includes ability immunities`() {
        val p = defensiveProfile(chart, PokemonType.WATER to PokemonType.FLYING, "volt-absorb")
        assertTrue(p.immunities.contains(PokemonType.ELECTRIC))
        assertTrue(p.immunities.contains(PokemonType.GROUND))
        assertFalse(p.weaknesses.contains(PokemonType.ELECTRIC))
    }
}
