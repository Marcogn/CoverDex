package com.marcogn.coverdex.domain.suggestion

import com.marcogn.coverdex.domain.coverage.analyseTeam
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart

/**
 * A direct port of `legacy-web/src/hooks/suggestionEngine.ts` — same function names, same
 * ranking, same top-5-per-`SuggestionPanel` cut left to the UI (this file, like the original,
 * returns every ranked candidate; `useSuggestions.test.ts`'s "top 10 candidates returned (not 5)"
 * confirms `computeSuggestions` itself never caps).
 *
 * One intentional deviation from the TypeScript, per
 * `docs/plan/phase-4-suggestions-and-generator.md` §2: the generation filter uses
 * [PokemonEntry.generationIntroduced] (Phase 1's real `pokemon_species.generation_id`) instead of
 * `suggestionEngine.ts`'s hardcoded `GEN_RANGES` id buckets, which wrongly bucket all 327
 * alternate forms with id > 10000 into Generation IX — see `reference-pokedata.md` §4. There is
 * no `GEN_RANGES` here; [SuggestionOptions.generation] is a generation number directly, `null`
 * meaning "all".
 */

data class Suggestion(
    val kind: Kind,
    val candidate: TeamMember,
    val candidateLabel: String,
    val types: Pair<PokemonType, PokemonType?>,
    val replacesMemberId: String? = null,
    val replacesName: String? = null,
    val newlyCovered: List<PokemonType>,
    val gain: Int,
    val compositeScore: Double,
    val newWeaknesses: List<PokemonType>,
    val aggravatedWeaknesses: List<PokemonType>,
    val aggravatedMembers: Map<PokemonType, List<String>>,
    /** `null` for a custom candidate (no catalogue entry to look one up on) — see
     * docs/plan/phase-7-accuracy-and-customization.md §5. Ranking's tie-break only; never the
     * primary sort. */
    val baseStatTotal: Int? = null,
) {
    enum class Kind { ADD, REPLACE }
}

data class SuggestionOptions(
    val includeCustoms: Boolean,
    val excludeLegendaries: Boolean = false,
    /** A generation number (1-9), or `null` for "all generations" — see the class doc above. */
    val generation: Int? = null,
)

/** Turns a catalogue entry into a candidate [TeamMember] with no moves — candidates are always
 * evaluated by types only. Ports `memberFromEntry`; `spriteUrl` is dropped, since this codebase
 * derives sprite URLs from [TeamMember.pokedexId] rather than storing them (see
 * `domain/sprite/SpriteUrlResolver.kt`), unlike the TypeScript `TeamMember.spriteUrl` field.
 * [TeamMember.ability] is [PokemonEntry.defaultAbility] rather than the TypeScript's always-absent
 * ability field — a deliberate native addition (not a port), since a candidate scored without the
 * ability it would actually carry once applied is exactly finding 6 in
 * `docs/post-migration-review.md`. */
fun memberFromEntry(e: PokemonEntry): TeamMember = TeamMember(
    id = "cand-${e.id}",
    pokedexId = e.id,
    speciesName = e.displayName,
    types = e.types,
    ability = e.defaultAbility,
    moves = List(4) { null },
    isCustomSaved = false,
)

/** Looks a candidate/member up in [pool] by species name — displayName match first, then a
 * lowercased raw identifier match, same precedence `pool.find { it.displayName == speciesName ||
 * it.name == speciesName.lowercase() }` had. Built once per [computeSuggestions] call instead of
 * scanned linearly per candidate: with the full ~1351-entry pool this was on the order of 1.8M
 * string comparisons per recomputation (once per candidate, plus once per team member for the
 * legendary filter) — see docs/plan/phase-7-accuracy-and-customization.md §0.7/§5.4. */
private class EntryLookup(pool: List<PokemonEntry>) {
    private val byDisplayName: Map<String, PokemonEntry> = buildMap { pool.forEach { putIfAbsent(it.displayName, it) } }
    private val byName: Map<String, PokemonEntry> = buildMap { pool.forEach { putIfAbsent(it.name, it) } }

    fun find(speciesName: String): PokemonEntry? = byDisplayName[speciesName] ?: byName[speciesName.lowercase()]
}

private data class RankedCandidate(
    val candidate: TeamMember,
    val result: CompositeScoreResult,
    val bestScore: Double,
    val replaceMember: TeamMember?,
    val isFinal: Boolean,
    val entryId: Int,
    val baseStatTotal: Int?,
)

private val rankingComparator = compareByDescending<RankedCandidate> { it.bestScore }
    .thenByDescending { it.isFinal }
    .thenByDescending { it.baseStatTotal ?: -1 }
    .thenBy { it.entryId }

/**
 * Pure suggestion ranking. Ports `computeSuggestions`: filter the pool to final evolutions,
 * apply the generation and legendary filters, drop species already on the team, then rank by
 * [computeCompositeScore] — addition mode (team of fewer than six) scores each candidate against
 * the whole team, replacement mode (a full team of six) finds each candidate's best member to
 * replace.
 */
fun computeSuggestions(
    chart: TypeChart,
    members: List<TeamMember>,
    pool: List<PokemonEntry>,
    customs: List<TeamMember>,
    options: SuggestionOptions,
    /** Resolves a catalogue entry's base stat total for the ranking's tie-break — defaults to its
     * current, latest-generation value ([PokemonEntry.baseStatTotal]); a caller building the
     * pool for a specific `options.generation` passes a generation-aware resolver instead. See
     * docs/plan/phase-7-accuracy-and-customization.md §5.2. Domain code stays Room-free: the
     * caller resolves historical BST ahead of time and hands in this closure, same pattern as
     * `domain/showdown/ShowdownFormat.kt`'s `resolveMove`/`resolveSpecies`. */
    bstFor: (PokemonEntry) -> Int? = { it.baseStatTotal },
): List<Suggestion> {
    if (pool.isEmpty() && (!options.includeCustoms || customs.isEmpty())) return emptyList()

    val entryLookup = EntryLookup(pool)

    var filtered = pool.filter { it.isFinalEvolution }

    if (options.generation != null) {
        filtered = filtered.filter { it.generationIntroduced == options.generation }
    }

    if (options.excludeLegendaries) {
        val teamHasLegendary = members.any { m ->
            val entry = entryLookup.find(m.speciesName)
            entry != null && (entry.isLegendary || entry.isMythical)
        }
        if (!teamHasLegendary) {
            filtered = filtered.filter { !it.isLegendary && !it.isMythical }
        }
    }

    val candidatePool = mutableListOf<TeamMember>()
    filtered.mapTo(candidatePool) { memberFromEntry(it) }
    if (options.includeCustoms) candidatePool.addAll(customs)

    val teamSpeciesKeys = members.map { it.speciesName.lowercase() }.toSet()
    val dedupCandidates = candidatePool.filter { it.speciesName.lowercase() !in teamSpeciesKeys }

    val teamAnalysis = analyseTeam(chart, members)

    val seen = mutableSetOf<String>()
    val deduped = dedupCandidates.filter { seen.add(it.speciesName.lowercase()) }

    val ranked: List<RankedCandidate> = if (members.size < 6) {
        // Every candidate is scored against the same team, so this is built once, not once per
        // candidate — see docs/post-migration-review.md, finding 3.
        val context = teamScoringContext(chart, members)
        deduped.map { cand ->
            val result = computeCompositeScore(chart, cand, context, teamAnalysis.unionCovered)
            val entry = entryLookup.find(cand.speciesName)
            RankedCandidate(
                candidate = cand,
                result = result,
                bestScore = result.compositeScore,
                replaceMember = null,
                isFinal = entry?.isFinalEvolution ?: false,
                entryId = entry?.id ?: Int.MAX_VALUE,
                baseStatTotal = entry?.let(bstFor),
            )
        }
    } else {
        // Only 6 distinct "team minus one member" contexts exist, regardless of how many
        // candidates are scored against them — build each one once, not once per candidate. Before
        // this, every one of the N candidates recomputed all 6 from scratch (N × 6 calls instead
        // of 6); see docs/post-migration-review.md, finding 3.
        val replacementContexts = members.map { m -> m to teamScoringContext(chart, members.filter { it.id != m.id }) }
        deduped.map { cand ->
            var bestScore = Double.NEGATIVE_INFINITY
            var bestMember = replacementContexts[0].first
            var bestResult = computeCompositeScore(chart, cand, replacementContexts[0].second, teamAnalysis.unionCovered)
            for ((m, context) in replacementContexts) {
                val result = computeCompositeScore(chart, cand, context, teamAnalysis.unionCovered)
                if (result.compositeScore > bestScore) {
                    bestScore = result.compositeScore
                    bestMember = m
                    bestResult = result
                }
            }
            val entry = entryLookup.find(cand.speciesName)
            RankedCandidate(
                candidate = cand,
                result = bestResult,
                bestScore = bestScore,
                replaceMember = bestMember,
                isFinal = entry?.isFinalEvolution ?: false,
                entryId = entry?.id ?: Int.MAX_VALUE,
                baseStatTotal = entry?.let(bstFor),
            )
        }
    }

    val sorted = ranked.sortedWith(rankingComparator)

    return if (members.size < 6) {
        sorted.map { r ->
            Suggestion(
                kind = Suggestion.Kind.ADD,
                candidate = r.candidate,
                candidateLabel = r.candidate.speciesName,
                types = r.candidate.types,
                newlyCovered = r.result.newlyCovered,
                gain = r.result.newlyCovered.size,
                compositeScore = r.result.compositeScore,
                newWeaknesses = r.result.newWeaknesses,
                aggravatedWeaknesses = r.result.aggravatedWeaknesses,
                aggravatedMembers = r.result.aggravatedMembers,
                baseStatTotal = r.baseStatTotal,
            )
        }
    } else {
        sorted.map { r ->
            Suggestion(
                kind = Suggestion.Kind.REPLACE,
                candidate = r.candidate,
                candidateLabel = r.candidate.speciesName,
                types = r.candidate.types,
                replacesMemberId = r.replaceMember?.id,
                replacesName = r.replaceMember?.speciesName,
                newlyCovered = r.result.newlyCovered,
                gain = r.result.offensiveGain,
                compositeScore = r.bestScore,
                newWeaknesses = r.result.newWeaknesses,
                aggravatedWeaknesses = r.result.aggravatedWeaknesses,
                aggravatedMembers = r.result.aggravatedMembers,
                baseStatTotal = r.baseStatTotal,
            )
        }
    }
}
