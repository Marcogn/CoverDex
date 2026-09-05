package com.marcogn.coverdex.domain.suggestion

import com.marcogn.coverdex.domain.coverage.defensiveMultiplier
import com.marcogn.coverdex.domain.coverage.offensiveCoverageForMember
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart

/**
 * The composite score shared by the suggestion engine
 * ([computeSuggestions][com.marcogn.coverdex.domain.suggestion.computeSuggestions]) and the team
 * generator (`domain/generator/TeamGenerator.kt`) — a direct port of `suggestionEngine.ts`'s
 * `computeCompositeScore`/`getWeaknesses`, kept in one place per
 * `docs/plan/phase-4-suggestions-and-generator.md` §1: "The 0.5 and 1.0 are load-bearing and
 * shared with the generator." Changing either weight means updating `native-spec.md`,
 * `CLAUDE.md` and every test asserting these values in the same commit.
 */
const val NEW_WEAKNESS_PENALTY = 0.5
const val AGGRAVATED_WEAKNESS_PENALTY = 1.0

/** Types weak (>1x) against [types], evaluated by types only — the same defensive check
 * `suggestionEngine.ts` and `teamGenerator.ts` both duplicate as a private `getWeaknesses`. */
fun weaknesses(chart: TypeChart, types: Pair<PokemonType, PokemonType?>): List<PokemonType> =
    PokemonType.entries.filter { atk -> defensiveMultiplier(chart, atk, types) > 1.0 }

data class CompositeScoreResult(
    val compositeScore: Double,
    val offensiveGain: Int,
    val newlyCovered: List<PokemonType>,
    val newWeaknesses: List<PokemonType>,
    val aggravatedWeaknesses: List<PokemonType>,
    val aggravatedMembers: Map<PokemonType, List<String>>,
)

/**
 * The half of [computeCompositeScore]'s work that depends only on a team, never on the candidate
 * being scored against it — build once per distinct `otherMembers` set with [teamScoringContext]
 * and reuse it across every candidate, instead of recomputing it from scratch for each one. Before
 * this existed, the suggestion engine's replacement mode (`SuggestionEngine.kt`) recomputed it
 * N × 6 times (once per candidate, per team member considered for replacement) when only 6 distinct
 * values are possible; see `docs/post-migration-review.md`, finding 3.
 */
class TeamScoringContext internal constructor(
    val baseCoverage: Set<PokemonType>,
    val otherWeaknessMap: Map<PokemonType, List<String>>,
)

fun teamScoringContext(chart: TypeChart, otherMembers: List<TeamMember>): TeamScoringContext {
    val baseCov = mutableSetOf<PokemonType>()
    otherMembers.forEach { baseCov.addAll(offensiveCoverageForMember(chart, it, false)) }

    val otherWeaknessMap = mutableMapOf<PokemonType, MutableList<String>>()
    for (m in otherMembers) {
        for (w in weaknesses(chart, m.types)) {
            otherWeaknessMap.getOrPut(w) { mutableListOf() }.add(m.speciesName)
        }
    }
    return TeamScoringContext(baseCov, otherWeaknessMap)
}

/**
 * Composite score for [candidate] joining a team whose other members are summarized by [context]
 * (see [teamScoringContext]), measured against [currentTeamCoverage] (the real team's union
 * coverage — kept as its own parameter rather than re-derived from the context, since replacement
 * mode compares against the *actual* team, not the hypothetical one with a member removed). Ports
 * `suggestionEngine.ts`'s `computeCompositeScore` verbatim, [candidate] and the team evaluated by
 * types only (`offensiveCoverageForMember(chart, member, useMoves = false)`), never by moves — see
 * `phase-4-suggestions-and-generator.md` §1.7.
 */
fun computeCompositeScore(
    chart: TypeChart,
    candidate: TeamMember,
    context: TeamScoringContext,
    currentTeamCoverage: Set<PokemonType>,
): CompositeScoreResult {
    val candCov = offensiveCoverageForMember(chart, candidate, false)

    val newUnion = mutableSetOf<PokemonType>().apply {
        addAll(context.baseCoverage)
        addAll(candCov)
    }

    val offensiveGain = newUnion.size - currentTeamCoverage.size
    val newlyCovered = newUnion.filter { it !in currentTeamCoverage }

    val candWeaknesses = weaknesses(chart, candidate.types)

    val newWeaknesses = mutableListOf<PokemonType>()
    val aggravatedWeaknesses = mutableListOf<PokemonType>()
    val aggravatedMembers = mutableMapOf<PokemonType, List<String>>()

    for (w in candWeaknesses) {
        val membersWithSameWeakness = context.otherWeaknessMap[w]
        if (!membersWithSameWeakness.isNullOrEmpty()) {
            aggravatedWeaknesses.add(w)
            aggravatedMembers[w] = membersWithSameWeakness
        } else {
            newWeaknesses.add(w)
        }
    }

    val compositeScore = offensiveGain -
        NEW_WEAKNESS_PENALTY * newWeaknesses.size -
        AGGRAVATED_WEAKNESS_PENALTY * aggravatedWeaknesses.size

    return CompositeScoreResult(
        compositeScore = compositeScore,
        offensiveGain = offensiveGain,
        newlyCovered = newlyCovered,
        newWeaknesses = newWeaknesses,
        aggravatedWeaknesses = aggravatedWeaknesses,
        aggravatedMembers = aggravatedMembers,
    )
}
