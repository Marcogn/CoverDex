package com.marcogn.coverdex.domain.coverage

import com.marcogn.coverdex.domain.ability.AbilityEffect
import com.marcogn.coverdex.domain.ability.AbilityEffectSide
import com.marcogn.coverdex.domain.ability.getAbilityEffects
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
 * across both defender types (stacking — never additive). When an ability is provided, immunity
 * and multiplier effects are applied.
 */
fun defensiveMultiplier(
    chart: TypeChart,
    attackingType: PokemonType,
    defenderTypes: Pair<PokemonType, PokemonType?>,
    ability: String? = null,
): Double {
    val t1 = chart.multiplier(attackingType, defenderTypes.first)
    val t2 = defenderTypes.second?.let { chart.multiplier(attackingType, it) } ?: 1.0
    var result = t1 * t2

    val effects = getAbilityEffects(ability)
    if (effects != null) {
        for (effect in effects) {
            when (effect) {
                is AbilityEffect.Immunity -> if (effect.type == attackingType) return 0.0
                is AbilityEffect.Multiplier ->
                    if (effect.side == AbilityEffectSide.DEFENSIVE && effect.type == attackingType) result *= effect.factor
                is AbilityEffect.BadgeOnly -> Unit
            }
        }
    }

    return result
}

private fun damagingMoveTypes(member: TeamMember): List<PokemonType> =
    member.moves.filterNotNull()
        .filter { it.damageClass != DamageClass.STATUS && (it.power ?: 0) > 0 }
        .map { it.type }

/** Types this member can hit super-effectively (>=2x). */
fun offensiveCoverageForMember(chart: TypeChart, member: TeamMember, useMoves: Boolean): Set<PokemonType> {
    val out = mutableSetOf<PokemonType>()
    val attackingTypes = if (useMoves) {
        damagingMoveTypes(member)
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
        val attackingTypes = if (useMoves) damagingMoveTypes(m) else listOfNotNull(m.types.first, m.types.second)
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
): DefensiveProfile {
    val weaknesses = mutableListOf<PokemonType>()
    val resistances = mutableListOf<PokemonType>()
    val immunities = mutableListOf<PokemonType>()
    for (atk in PokemonType.entries) {
        when (val m = defensiveMultiplier(chart, atk, types, ability)) {
            0.0 -> immunities.add(atk)
            else -> if (m > 1.0) weaknesses.add(atk) else if (m < 1.0) resistances.add(atk)
        }
    }
    return DefensiveProfile(weaknesses, resistances, immunities)
}

/** Types that hit 2+ members for super-effective damage. */
fun sharedWeaknesses(chart: TypeChart, members: List<TeamMember>): List<PokemonType> {
    val result = mutableListOf<PokemonType>()
    for (atk in PokemonType.entries) {
        val count = members.count { defensiveMultiplier(chart, atk, it.types, it.ability) > 1.0 }
        if (count >= 2) result.add(atk)
    }
    return result
}
