package com.marcogn.coverdex.domain.coverage

import com.marcogn.coverdex.domain.ability.applyAbilityEffects
import com.marcogn.coverdex.domain.ability.bypassesGhostImmunity
import com.marcogn.coverdex.domain.ability.getAbilityEffects
import com.marcogn.coverdex.domain.ability.overriddenMoveType
import com.marcogn.coverdex.domain.item.ItemEffect
import com.marcogn.coverdex.domain.item.getItemEffects
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart

/**
 * A direct port of `legacy-web/src/utils/coverageEngine.ts` — same function names, same
 * signatures (`offensiveCoverageForMember`'s explicit `useMoves` flag included, despite
 * `phase-3-analysis.md`'s own pseudocode omitting it — see
 * `docs/implementation-decisions.md`, "Phase 3"), same order of operations. Resist making this
 * more idiomatic than the original: the value of this port is that a reviewer can put the two
 * files side by side.
 */

/**
 * Compute defensive effectiveness on a defender with one or two types. Multiplies effectiveness
 * across both defender types (stacking — never additive), then applies [ability] and [item]
 * effects in the fixed order docs/plan/phase-7-accuracy-and-customization.md §4.2 specifies:
 *
 * 1. The type chart product, above.
 * 2. Item [ItemEffect.RemovesTypeImmunities]/[ItemEffect.GroundsHolder] cancel a `0.0` that came
 *    from the type chart itself — Ring Target for any type, Iron Ball only for a Ground attack.
 * 3. Ability immunity (skipped entirely for a Ground attack when Iron Ball is held — it
 *    neutralizes an ability-granted Ground immunity too, e.g. Levitate/Earth Eater, not just the
 *    type chart's).
 * 4. Item [ItemEffect.Immunity] (Air Balloon) — same Iron Ball exception as step 3.
 * 5. Ability multiplier (Thick Fat, Heatproof, Dry Skin's Fire 1.25x, ...).
 * 6. Ability super-effective reducer/cap (Filter/Solid Rock/Prism Armor, Delta Stream) — only
 *    once the running value is already `> 1.0`.
 * 7. Item [ItemEffect.ResistBerry] — `x0.5` once the running value is `> 1.0`, or unconditionally
 *    for Chilan Berry ([ItemEffect.ResistBerry.alwaysApplies]).
 *
 * Steps 3+5+6 are one call to [applyAbilityEffects]; steps 2/4/7 are items, interleaved around it
 * rather than folded into the same helper, since Iron Ball's step-3/4 skip couldn't otherwise be
 * expressed without a callback.
 */
fun defensiveMultiplier(
    chart: TypeChart,
    attackingType: PokemonType,
    defenderTypes: Pair<PokemonType, PokemonType?>,
    ability: String? = null,
    item: String? = null,
): Double {
    val t1 = chart.multiplier(attackingType, defenderTypes.first)
    val t2 = defenderTypes.second?.let { chart.multiplier(attackingType, it) } ?: 1.0
    val chartProduct = t1 * t2

    val itemEffects = getItemEffects(item).orEmpty()
    val groundsHolder = attackingType == PokemonType.GROUND && itemEffects.any { it is ItemEffect.GroundsHolder }
    val removesTypeImmunities = itemEffects.any { it is ItemEffect.RemovesTypeImmunities }

    val afterTypeChartCancel = if (chartProduct == 0.0 && (removesTypeImmunities || groundsHolder)) 1.0 else chartProduct

    if (!groundsHolder && itemEffects.any { it is ItemEffect.Immunity && it.type == attackingType }) return 0.0

    val abilityEffects = if (groundsHolder) null else getAbilityEffects(ability)
    val afterAbility = applyAbilityEffects(afterTypeChartCancel, attackingType, abilityEffects)

    val resistBerry = itemEffects.filterIsInstance<ItemEffect.ResistBerry>().find { it.type == attackingType }
    return if (resistBerry != null && (resistBerry.alwaysApplies || afterAbility > 1.0)) afterAbility * 0.5 else afterAbility
}

private fun damagingMoveTypes(member: TeamMember): List<PokemonType> =
    member.moves.filterNotNull()
        .filter { it.damageClass != DamageClass.STATUS && (it.power ?: 0) > 0 }
        .map { it.type }

/** Types this member can hit super-effectively (>=2x). [useMoves] gates the -ate/Normalize
 * offensive rewrite too, not just which attacking types are used: type-based coverage (no moves
 * entered) has no real "Normal-type move" for those abilities to rewrite, only the member's own
 * typing — see docs/plan/phase-7-accuracy-and-customization.md §7.2. */
fun offensiveCoverageForMember(chart: TypeChart, member: TeamMember, useMoves: Boolean): Set<PokemonType> {
    val out = mutableSetOf<PokemonType>()
    val attackingTypes = if (useMoves) {
        damagingMoveTypes(member).map { overriddenMoveType(member.ability, it) }
    } else {
        listOfNotNull(member.types.first, member.types.second)
    }
    for (atk in attackingTypes) {
        for (def in PokemonType.entries) {
            // For coverage we treat the defender as a single-type opponent.
            if (chart.multiplier(atk, def) >= 2.0) out.add(def)
        }
    }
    return out
}

/** True if this member has at least one damaging move entered. */
fun memberHasMoves(member: TeamMember): Boolean =
    member.moves.any { it != null && it.damageClass != DamageClass.STATUS && (it.power ?: 0) > 0 }

/** The attacking types a single member's row in the offensive/defensive grids actually uses —
 * damaging move types when it has any, its own (possibly overridden) types otherwise. Ports
 * `CoverageGrid.tsx`'s `collectAttackingTypes` as a call to the shared [memberHasMoves] filter
 * instead of repeating that filter inline a second time, per `phase-3-analysis.md`'s note on
 * that file's line 333. */
fun attackingTypesForMember(member: TeamMember): List<PokemonType> =
    if (memberHasMoves(member)) damagingMoveTypes(member) else listOfNotNull(member.types.first, member.types.second)

enum class CoverageMode { MOVES, TYPES }

data class TeamCoverage(
    val perMemberCovered: Map<String, Set<PokemonType>>,
    val unionCovered: Set<PokemonType>,
    val uncovered: List<PokemonType>,
    /** Best offensive multiplier on a generic mono-type defender, by defending type. */
    val bestMultiplierByType: Map<PokemonType, Double>,
    val modePerMember: Map<String, CoverageMode>,
    val mixed: Boolean,
)

fun analyseTeam(chart: TypeChart, members: List<TeamMember>): TeamCoverage {
    val perMemberCovered = mutableMapOf<String, Set<PokemonType>>()
    val modePerMember = mutableMapOf<String, CoverageMode>()
    val allHaveMoves = members.isNotEmpty() && members.all { memberHasMoves(it) }
    val noneHaveMoves = members.all { !memberHasMoves(it) }
    val mixed = !allHaveMoves && !noneHaveMoves

    for (m in members) {
        val useMoves = memberHasMoves(m)
        modePerMember[m.id] = if (useMoves) CoverageMode.MOVES else CoverageMode.TYPES
        perMemberCovered[m.id] = offensiveCoverageForMember(chart, m, useMoves)
    }

    val union = mutableSetOf<PokemonType>()
    perMemberCovered.values.forEach { union.addAll(it) }
    val uncovered = PokemonType.entries.filter { it !in union }

    val best = PokemonType.entries.associateWithTo(mutableMapOf()) { 0.0 }
    for (m in members) {
        val useMoves = modePerMember[m.id] == CoverageMode.MOVES
        val attackingTypes = if (useMoves) {
            damagingMoveTypes(m).map { overriddenMoveType(m.ability, it) }
        } else {
            listOfNotNull(m.types.first, m.types.second)
        }
        for (atk in attackingTypes) {
            for (def in PokemonType.entries) {
                val mult = chart.multiplier(atk, def)
                if (mult > (best[def] ?: 0.0)) best[def] = mult
            }
        }
    }
    return TeamCoverage(perMemberCovered, union, uncovered, best, modePerMember, mixed)
}

data class DefensiveProfile(
    /** >1x incoming. */
    val weaknesses: List<PokemonType>,
    /** <1x and >0. */
    val resistances: List<PokemonType>,
    /** 0x. */
    val immunities: List<PokemonType>,
)

fun defensiveProfile(
    chart: TypeChart,
    types: Pair<PokemonType, PokemonType?>,
    ability: String? = null,
    item: String? = null,
): DefensiveProfile {
    val weaknesses = mutableListOf<PokemonType>()
    val resistances = mutableListOf<PokemonType>()
    val immunities = mutableListOf<PokemonType>()
    for (atk in PokemonType.entries) {
        when (val m = defensiveMultiplier(chart, atk, types, ability, item)) {
            0.0 -> immunities.add(atk)
            else -> if (m > 1.0) weaknesses.add(atk) else if (m < 1.0) resistances.add(atk)
        }
    }
    return DefensiveProfile(weaknesses, resistances, immunities)
}

/** How many team members each type hits super-effectively, keyed in [PokemonType] order — the
 * count [sharedWeaknesses] itself throws away once it filters to >=2. `CoverageGrid.tsx`
 * recomputes this exact loop inline for its "×N" shared-weakness badge rather than reusing
 * `sharedWeaknesses`, since that function only returns membership; exposed here instead so the
 * UI calls one shared function for both, per the same "call the shared function, don't repeat
 * the filter" principle `phase-3-analysis.md` states for `collectAttackingTypes`. */
fun sharedWeaknessCounts(chart: TypeChart, members: List<TeamMember>): Map<PokemonType, Int> =
    PokemonType.entries.associateWith { atk -> members.count { defensiveMultiplier(chart, atk, it.types, it.ability, it.item) > 1.0 } }

/** Types that hit 2+ members for super-effective damage. */
fun sharedWeaknesses(chart: TypeChart, members: List<TeamMember>): List<PokemonType> =
    sharedWeaknessCounts(chart, members).filterValues { it >= 2 }.keys.toList()

/** This member's best offensive multiplier against each defending type — one row of the
 * offensive coverage grid ([TeamCoverage.bestMultiplierByType] is the team-aggregate "best" row
 * the same grid needs below it). Not one of `coverageEngine.ts`'s seven exported functions —
 * `CoverageGrid.tsx` computes this inline per grid cell instead of exporting it; centralized here
 * so it is unit-testable rather than buried in Compose code, per this codebase's own "pure logic
 * lives in domain/" convention (see `docs/implementation-decisions.md`, "Phase 3"). */
fun offensiveMultipliersForMember(chart: TypeChart, member: TeamMember): Map<PokemonType, Double> {
    val rawAttackingTypes = attackingTypesForMember(member)
    // The -ate/Normalize rewrite only applies to a real move, same gate as
    // offensiveCoverageForMember's useMoves check above.
    val attackingTypes = if (memberHasMoves(member)) {
        rawAttackingTypes.map { overriddenMoveType(member.ability, it) }
    } else {
        rawAttackingTypes
    }
    val bypassGhost = bypassesGhostImmunity(member.ability)
    return PokemonType.entries.associateWith { def ->
        attackingTypes.maxOfOrNull { atk ->
            val mult = chart.multiplier(atk, def)
            // Scrappy/Mind's Eye: Normal/Fighting moves hit Ghost neutrally instead of being
            // blocked. Never crosses the >=2x coverage threshold, so this is grid-display only —
            // see overriddenMoveType's/bypassesGhostImmunity's own docs.
            if (bypassGhost && mult == 0.0 && def == PokemonType.GHOST && (atk == PokemonType.NORMAL || atk == PokemonType.FIGHTING)) {
                1.0
            } else {
                mult
            }
        } ?: 0.0
    }
}

/** The defensive grid's "most vulnerable" row: the worst (highest) multiplier any member takes
 * from each attacking type. */
fun mostVulnerableByType(chart: TypeChart, members: List<TeamMember>): Map<PokemonType, Double> =
    PokemonType.entries.associateWith { atk -> members.maxOfOrNull { m -> defensiveMultiplier(chart, atk, m.types, m.ability, m.item) } ?: 0.0 }
