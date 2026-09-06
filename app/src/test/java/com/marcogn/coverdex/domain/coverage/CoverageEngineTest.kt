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
    fun `wonder-guard blocks a non-super-effective hit but lets a super-effective one through unchanged`() {
        // Fire vs Ghost is neutral (1x) in the fixture — Wonder Guard blocks it entirely.
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GHOST to null, "wonder-guard"), 0.0)
        // Dark vs Ghost is 2x (super-effective) — passes through unchanged, real damage happens.
        assertEquals(2.0, defensiveMultiplier(chart, PokemonType.DARK, PokemonType.GHOST to null, "wonder-guard"), 0.0)
    }

    @Test
    fun `heatproof and water-bubble both halve Fire damage`() {
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, "heatproof"), 0.0)
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, "water-bubble"), 0.0)
    }

    @Test
    fun `purifying-salt halves Ghost damage`() {
        // Ghost vs Fire is neutral (1x, not immune) in the fixture.
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.GHOST, PokemonType.FIRE to null, "purifying-salt"), 0.0)
    }

    @Test
    fun `dry-skin absorbs Water and takes extra Fire damage`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.WATER, PokemonType.NORMAL to null, "dry-skin"), 0.0)
        assertEquals(1.25, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, "dry-skin"), 0.0)
    }

    @Test
    fun `filter, solid-rock and prism-armor each reduce an already super-effective hit by a quarter`() {
        // Fire vs Grass is 2x in the fixture.
        for (ability in listOf("filter", "solid-rock", "prism-armor")) {
            assertEquals(1.5, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, ability), 0.0)
        }
    }

    @Test
    fun `filter turns a x4 weakness into x3`() {
        // Ice vs Grass/Ground is 2x * 2x = 4x in the fixture.
        assertEquals(3.0, defensiveMultiplier(chart, PokemonType.ICE, PokemonType.GRASS to PokemonType.GROUND, "filter"), 0.0)
    }

    @Test
    fun `filter does not touch a neutral or resisted hit`() {
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, "filter"), 0.0)
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.FIRE to null, "filter"), 0.0)
    }

    @Test
    fun `delta-stream caps a super-effective hit against the holder at neutral`() {
        // Electric vs Flying is 2x in the fixture.
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.ELECTRIC, PokemonType.FLYING to null, "delta-stream"), 0.0)
    }

    @Test
    fun `primordial-sea and desolate-land are immune to Fire and Water respectively`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, "primordial-sea"), 0.0)
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.WATER, PokemonType.GRASS to null, "desolate-land"), 0.0)
    }

    @Test
    fun `tera-shell is badge-only, no multiplier change`() {
        assertEquals(2.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, "tera-shell"), 0.0)
    }

    // --- offensive ability gap (overriddenMoveType / bypassesGhostImmunity, §7.2) ---

    @Test
    fun `refrigerate rewrites a Normal move to Ice for both offensiveCoverageForMember and the grid`() {
        val member = buildMember("Vaporeon", PokemonType.WATER to null, listOf(PokemonType.NORMAL), ability = "refrigerate")

        val coverage = offensiveCoverageForMember(chart, member, useMoves = true)
        assertTrue(coverage.contains(PokemonType.GRASS)) // Ice hits Grass 2x; a bare Normal move would not

        val multipliers = offensiveMultipliersForMember(chart, member)
        assertEquals(2.0, multipliers.getValue(PokemonType.GRASS), 0.0)
    }

    @Test
    fun `the -ate abilities never rewrite a move that already has a type`() {
        val member = buildMember("Vaporeon", PokemonType.WATER to null, listOf(PokemonType.WATER), ability = "aerilate")

        val coverage = offensiveCoverageForMember(chart, member, useMoves = true)
        assertFalse(coverage.contains(PokemonType.FIGHTING)) // Flying would hit Fighting 2x; Water does not
    }

    @Test
    fun `normalize rewrites every move to Normal, not just Normal-type ones`() {
        val member = buildMember("Vaporeon", PokemonType.WATER to null, listOf(PokemonType.GRASS), ability = "normalize")

        // Grass hits Ground 2x; Normal does not, so this proves the Grass move was rewritten away.
        val coverage = offensiveCoverageForMember(chart, member, useMoves = true)
        assertFalse(coverage.contains(PokemonType.GROUND))
    }

    @Test
    fun `the -ate abilities do not apply to type-based coverage, only real moves`() {
        // No moves entered — Ditto's own type (Normal) is used as the stand-in attack. If
        // refrigerate wrongly applied here too, Normal would become Ice and Grass (2x to Ice)
        // would show up in coverage; Normal itself never hits Grass for 2x.
        val member = buildMember("Ditto", PokemonType.NORMAL to null, ability = "refrigerate")

        val coverage = offensiveCoverageForMember(chart, member, useMoves = false)
        assertFalse(coverage.contains(PokemonType.GRASS))
    }

    @Test
    fun `scrappy lets Normal and Fighting moves hit Ghost neutrally, in the grid only`() {
        val member = buildMember("Kecleon", PokemonType.NORMAL to null, listOf(PokemonType.NORMAL, PokemonType.FIGHTING), ability = "scrappy")

        val multipliers = offensiveMultipliersForMember(chart, member)
        assertEquals(1.0, multipliers.getValue(PokemonType.GHOST), 0.0)

        // Coverage (the >=2x scan) is untouched: 0x -> 1x never crosses the threshold.
        val coverage = offensiveCoverageForMember(chart, member, useMoves = true)
        assertFalse(coverage.contains(PokemonType.GHOST))
    }

    @Test
    fun `without scrappy, Normal and Fighting moves stay blocked by Ghost`() {
        val member = buildMember("Kecleon", PokemonType.NORMAL to null, listOf(PokemonType.NORMAL))

        val multipliers = offensiveMultipliersForMember(chart, member)
        assertEquals(0.0, multipliers.getValue(PokemonType.GHOST), 0.0)
    }

    // --- held items (§4, phase-7-accuracy-and-customization.md) ---

    @Test
    fun `air-balloon makes the holder immune to Ground regardless of a normal weakness`() {
        // Fire is normally 2x weak to Ground in the fixture.
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.FIRE to null, item = "air-balloon"), 0.0)
    }

    @Test
    fun `iron-ball grounds a Flying-type, cancelling the type chart's own Ground immunity`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.FLYING to null), 0.0)
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.FLYING to null, item = "iron-ball"), 0.0)
    }

    @Test
    fun `iron-ball also cancels an ability-granted Ground immunity (Levitate)`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.GHOST to PokemonType.POISON, ability = "levitate"), 0.0)
        assertEquals(
            2.0,
            defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.GHOST to PokemonType.POISON, ability = "levitate", item = "iron-ball"),
            0.0,
        )
    }

    @Test
    fun `ring-target removes a type-chart immunity`() {
        assertEquals(0.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.FLYING to null), 0.0)
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.GROUND, PokemonType.FLYING to null, item = "ring-target"), 0.0)
    }

    @Test
    fun `a resist berry halves an already super-effective hit of its type`() {
        // Fire vs Grass is 2x; Occa Berry resists Fire.
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, item = "occa-berry"), 0.0)
    }

    @Test
    fun `a resist berry does not touch a neutral or resisted hit`() {
        assertEquals(1.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.NORMAL to null, item = "occa-berry"), 0.0)
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.FIRE to null, item = "occa-berry"), 0.0)
    }

    @Test
    fun `chilan berry halves Normal damage even though Normal is never super-effective`() {
        assertEquals(0.5, defensiveMultiplier(chart, PokemonType.NORMAL, PokemonType.WATER to null, item = "chilan-berry"), 0.0)
    }

    @Test
    fun `an unmodelled item has no effect`() {
        assertEquals(2.0, defensiveMultiplier(chart, PokemonType.FIRE, PokemonType.GRASS to null, item = "leftovers"), 0.0)
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

    // --- grid-support functions (not part of the ported seven; see CoverageEngine.kt's doc
    // comments and docs/implementation-decisions.md, "Phase 3") ---

    @Test
    fun `sharedWeaknessCounts agrees with sharedWeaknesses' own 2-plus threshold`() {
        val team = listOf(
            buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING),
            buildMember("Pidgey", PokemonType.NORMAL to PokemonType.FLYING),
        )
        val counts = sharedWeaknessCounts(chart, team)
        val shared = sharedWeaknesses(chart, team)
        assertEquals(shared.toSet(), counts.filterValues { it >= 2 }.keys)
        assertEquals(2, counts[PokemonType.ROCK])
        assertEquals(2, counts[PokemonType.ELECTRIC])
    }

    @Test
    fun `offensiveMultipliersForMember uses move types when present, else the member's own types`() {
        val withMoves = buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING, listOf(PokemonType.FIRE, PokemonType.GROUND))
        val byMoves = offensiveMultipliersForMember(chart, withMoves)
        assertEquals(2.0, byMoves.getValue(PokemonType.ELECTRIC), 0.0) // from the Ground move, not from Fire/Flying types

        val noMoves = buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING)
        val byTypes = offensiveMultipliersForMember(chart, noMoves)
        assertEquals(1.0, byTypes.getValue(PokemonType.ELECTRIC), 0.0) // neither Fire nor Flying hits Electric for 2x+
    }

    @Test
    fun `mostVulnerableByType is the worst multiplier any member takes from each attacking type`() {
        val team = listOf(
            buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING), // 4x weak to Rock
            buildMember("Snorlax", PokemonType.NORMAL to null), // neutral to Rock
        )
        val worst = mostVulnerableByType(chart, team)
        assertEquals(4.0, worst.getValue(PokemonType.ROCK), 0.0)
    }

    // --- exhaustive type-chart / dual-typing sweep (§7.3.1-2, phase-7-accuracy-and-customization.md) ---

    private val validMultipliers = setOf(0.0, 0.5, 1.0, 2.0)

    @Test
    fun `every one of the 324 type chart cells is a real multiplier value`() {
        for (atk in PokemonType.entries) {
            for (def in PokemonType.entries) {
                val mult = chart.multiplier(atk, def)
                assertTrue("$atk -> $def was $mult, not one of $validMultipliers", mult in validMultipliers)
            }
        }
    }

    @Test
    fun `classic asymmetric matchups are exactly right`() {
        assertEquals(0.0, chart.multiplier(PokemonType.NORMAL, PokemonType.GHOST), 0.0)
        assertEquals(0.0, chart.multiplier(PokemonType.FIGHTING, PokemonType.GHOST), 0.0)
        assertEquals(0.0, chart.multiplier(PokemonType.GROUND, PokemonType.FLYING), 0.0)
        assertEquals(2.0, chart.multiplier(PokemonType.FAIRY, PokemonType.DRAGON), 0.0)
        assertEquals(2.0, chart.multiplier(PokemonType.STEEL, PokemonType.FAIRY), 0.0)
        assertEquals(2.0, chart.multiplier(PokemonType.FIRE, PokemonType.STEEL), 0.0)
        assertEquals(0.0, chart.multiplier(PokemonType.POISON, PokemonType.STEEL), 0.0)
        assertEquals(0.0, chart.multiplier(PokemonType.ELECTRIC, PokemonType.GROUND), 0.0)
    }

    /** The complete space of typings a real Pokemon can have: 18 single types plus every
     * unordered pair of two distinct types (18 + C(18,2) = 18 + 153 = 171). Enumerating all 1351+
     * catalogue forms would only re-test this same 171-element space with worse failure
     * messages — see docs/plan/phase-7-accuracy-and-customization.md §7.3.2. */
    private fun allTypings(): List<Pair<PokemonType, PokemonType?>> {
        val singles = PokemonType.entries.map { it to null }
        val pairs = PokemonType.entries.flatMapIndexed { i, t1 ->
            PokemonType.entries.drop(i + 1).map { t2 -> t1 to t2 }
        }
        return singles + pairs
    }

    @Test
    fun `allTypings enumerates exactly 171 typings, 18 single and 153 dual`() {
        val typings = allTypings()
        assertEquals(171, typings.size)
        assertEquals(18, typings.count { it.second == null })
        assertEquals(153, typings.count { it.second != null })
    }

    @Test
    fun `defensiveMultiplier for every one of the 171 typings equals the product of its two single-type lookups`() {
        for (types in allTypings()) {
            for (atk in PokemonType.entries) {
                val expected = chart.multiplier(atk, types.first) * (types.second?.let { chart.multiplier(atk, it) } ?: 1.0)
                val actual = defensiveMultiplier(chart, atk, types)
                assertEquals("$atk vs $types", expected, actual, 0.0)
            }
        }
    }

    @Test
    fun `defensiveProfile buckets every non-neutral type exactly once and omits every neutral one, for all 171 typings`() {
        // defensiveProfile's own contract (its class doc): weaknesses ">1x", resistances "<1x and
        // >0", immunities "0x" — a neutral (1x) matchup belongs to none of the three and is
        // implicit by omission, not a fourth bucket. This test's "no gap" is therefore about the
        // NON-neutral types only: every type whose real multiplier isn't 1.0 must appear in
        // exactly one bucket, and every bucketed type's real multiplier must match its bucket.
        for (types in allTypings()) {
            val profile = defensiveProfile(chart, types)
            val bucketed = profile.weaknesses + profile.resistances + profile.immunities

            assertEquals("$types: a type landed in more than one bucket", bucketed.size, bucketed.toSet().size)

            for (atk in PokemonType.entries) {
                val mult = defensiveMultiplier(chart, atk, types)
                when {
                    mult == 0.0 -> assertTrue("$types: $atk ($mult) should be an immunity", atk in profile.immunities)
                    mult > 1.0 -> assertTrue("$types: $atk ($mult) should be a weakness", atk in profile.weaknesses)
                    mult < 1.0 -> assertTrue("$types: $atk ($mult) should be a resistance", atk in profile.resistances)
                    else -> assertTrue("$types: $atk ($mult) is neutral, must not be bucketed", atk !in bucketed)
                }
            }
        }
    }
}
