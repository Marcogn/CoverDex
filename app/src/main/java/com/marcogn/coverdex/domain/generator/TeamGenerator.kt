package com.marcogn.coverdex.domain.generator

import com.marcogn.coverdex.domain.coverage.offensiveCoverageForMember
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.suggestion.computeCompositeScore
import com.marcogn.coverdex.domain.suggestion.memberFromEntry
import kotlin.random.Random

/**
 * A direct port of `legacy-web/src/hooks/teamGenerator.ts`. Randomness is injectable — a
 * [Random] parameter, defaulting to [Random.Default] — rather than a direct `Math.random()` call,
 * so tests can seed it; see `docs/plan/phase-4-suggestions-and-generator.md` §3.
 *
 * `buildEligiblePool`, `generateTeam` and `regenerateSlot` all drop the TypeScript's `customs:
 * TeamMember[]` parameter: reading `teamGenerator.ts` end to end shows it is never referenced in
 * any of the three function bodies (nor is `GeneratorConstraints.customSlots`, kept below only as
 * a ported struct field) — a genuinely dead parameter in the original, not a behavioural
 * difference. See `docs/implementation-decisions.md`, "Phase 4".
 */

/** Hardcoded per-generation lists of starter final evolutions (Grass/Fire/Water), ported
 * verbatim from `teamGenerator.ts`'s `STARTER_FINALS` — data, not logic; adding a generation
 * later means editing this map by hand, a known and accepted cost. */
val STARTER_FINALS: Map<Int, List<String>> = mapOf(
    1 to listOf("venusaur", "charizard", "blastoise"),
    2 to listOf("meganium", "typhlosion", "feraligatr"),
    3 to listOf("sceptile", "blaziken", "swampert"),
    4 to listOf("torterra", "infernape", "empoleon"),
    5 to listOf("serperior", "emboar", "samurott"),
    6 to listOf("chesnaught", "delphox", "greninja"),
    7 to listOf("decidueye", "incineroar", "primarina"),
    8 to listOf("rillaboom", "cinderace", "inteleon"),
    9 to listOf("meowscarada", "skeledirge", "quaquaval"),
)

private val ALL_STARTER_SPECIES: Set<String> = STARTER_FINALS.values.flatten().toSet()

data class GeneratorConstraints(
    val starterSlots: Int = 0,
    val legendaryMythicalSlots: Int = 0,
    val megaSlots: Int = 0,
    val dynamaxSlots: Int = 0,
    val customSlots: Int = 0,
)

val DEFAULT_CONSTRAINTS = GeneratorConstraints()

data class GeneratorResult(
    val team: List<TeamMember>,
    val warning: String? = null,
)

private fun isStarter(entry: PokemonEntry): Boolean = entry.speciesName.lowercase() in ALL_STARTER_SPECIES
private fun isLegendaryOrMythical(entry: PokemonEntry): Boolean = entry.isLegendary || entry.isMythical
private fun isMega(entry: PokemonEntry): Boolean = entry.name.lowercase().contains("-mega")
private fun isDynamax(entry: PokemonEntry): Boolean = entry.name.lowercase().contains("-gmax")
private fun isMegaOrDynamax(entry: PokemonEntry): Boolean = isMega(entry) || isDynamax(entry)

private fun findEntry(allPokemon: List<PokemonEntry>, m: TeamMember): PokemonEntry? =
    allPokemon.find { it.displayName == m.speciesName || it.name == m.speciesName.lowercase() }

private fun currentTeamCoverage(chart: TypeChart, team: List<TeamMember>): Set<PokemonType> {
    val cov = mutableSetOf<PokemonType>()
    team.forEach { cov.addAll(offensiveCoverageForMember(chart, it, false)) }
    return cov
}

/** Composite score for [candidate] relative to [currentTeam], plus a small random tie-breaking
 * factor. Ports `teamGenerator.ts`'s own `computeScore` as a thin wrapper over the shared
 * [computeCompositeScore] — `currentTeam` doubles as both the "other members" and the coverage
 * baseline, since the generator (unlike the suggestion engine's replacement mode) never excludes
 * a member from the comparison. */
private fun computeScore(chart: TypeChart, candidate: TeamMember, currentTeam: List<TeamMember>, random: Random): Double {
    val coverage = currentTeamCoverage(chart, currentTeam)
    val result = computeCompositeScore(chart, candidate, currentTeam, coverage)
    val noise = (random.nextDouble() - 0.5) * 0.02
    return result.compositeScore + noise
}

/** Build a filtered pool of eligible Pokémon from the full catalogue: final evolutions only,
 * Mega/Dynamax forms and legendaries/mythicals included only when their constraint slots are
 * greater than zero. */
fun buildEligiblePool(allPokemon: List<PokemonEntry>, constraints: GeneratorConstraints): List<PokemonEntry> {
    var pool = allPokemon.filter { it.isFinalEvolution }

    pool = when {
        constraints.megaSlots <= 0 && constraints.dynamaxSlots <= 0 -> pool.filterNot { isMegaOrDynamax(it) }
        constraints.megaSlots <= 0 -> pool.filterNot { isMega(it) }
        constraints.dynamaxSlots <= 0 -> pool.filterNot { isDynamax(it) }
        else -> pool
    }

    if (constraints.legendaryMythicalSlots <= 0) {
        pool = pool.filterNot { isLegendaryOrMythical(it) }
    }

    return pool
}

/**
 * Generate a team using the greedy coverage-maximizing algorithm. Enforces "exactly N" semantics
 * for each constrained category: reserved slots are filled first from the category sub-pool,
 * then free slots are filled from the unconstrained pool, respecting each category's cap.
 */
fun generateTeam(
    chart: TypeChart,
    allPokemon: List<PokemonEntry>,
    lockedMembers: List<TeamMember>,
    constraints: GeneratorConstraints,
    random: Random = Random.Default,
): GeneratorResult {
    val pool = buildEligiblePool(allPokemon, constraints)
    val slotsToFill = 6 - lockedMembers.size
    if (slotsToFill <= 0) {
        return GeneratorResult(team = lockedMembers.take(6))
    }

    val team = lockedMembers.toMutableList()
    val usedSpecies = team.mapTo(mutableSetOf()) { it.speciesName.lowercase() }

    var legendaryMythicalCount = team.count { m -> findEntry(allPokemon, m)?.let { isLegendaryOrMythical(it) } == true }
    var starterCount = team.count { m -> findEntry(allPokemon, m)?.let { isStarter(it) } == true }
    var megaCount = team.count { m -> findEntry(allPokemon, m)?.let { isMega(it) } == true }
    var dynamaxCount = team.count { m -> findEntry(allPokemon, m)?.let { isDynamax(it) } == true }

    var starterSlotsRemaining = maxOf(0, constraints.starterSlots - starterCount)
    var legendaryMythicalSlotsRemaining = maxOf(0, constraints.legendaryMythicalSlots - legendaryMythicalCount)
    var megaSlotsRemaining = maxOf(0, constraints.megaSlots - megaCount)
    var dynamaxSlotsRemaining = maxOf(0, constraints.dynamaxSlots - dynamaxCount)

    repeat(slotsToFill) {
        var candidatePool: List<PokemonEntry>

        if (legendaryMythicalSlotsRemaining > 0) {
            candidatePool = pool.filter { isLegendaryOrMythical(it) && it.displayName.lowercase() !in usedSpecies }
            legendaryMythicalSlotsRemaining--
        } else if (starterSlotsRemaining > 0) {
            candidatePool = pool.filter { isStarter(it) && it.displayName.lowercase() !in usedSpecies }
            starterSlotsRemaining--
        } else if (megaSlotsRemaining > 0) {
            candidatePool = pool.filter { isMega(it) && it.displayName.lowercase() !in usedSpecies }
            megaSlotsRemaining--
        } else if (dynamaxSlotsRemaining > 0) {
            candidatePool = pool.filter { isDynamax(it) && it.displayName.lowercase() !in usedSpecies }
            dynamaxSlotsRemaining--
        } else {
            var free = pool.filter { it.displayName.lowercase() !in usedSpecies }
            if (constraints.legendaryMythicalSlots > 0 && legendaryMythicalCount >= constraints.legendaryMythicalSlots) {
                free = free.filterNot { isLegendaryOrMythical(it) }
            }
            if (constraints.starterSlots > 0 && starterCount >= constraints.starterSlots) {
                free = free.filterNot { isStarter(it) }
            }
            if (constraints.megaSlots > 0 && megaCount >= constraints.megaSlots) {
                free = free.filterNot { isMega(it) }
            }
            if (constraints.dynamaxSlots > 0 && dynamaxCount >= constraints.dynamaxSlots) {
                free = free.filterNot { isDynamax(it) }
            }
            candidatePool = free
        }

        if (candidatePool.isEmpty()) {
            return GeneratorResult(team = team, warning = "tooFewPokemon")
        }

        val best = candidatePool
            .map { entry -> entry to memberFromEntry(entry) }
            .maxByOrNull { (_, member) -> computeScore(chart, member, team, random) }!!

        val newMember = best.second.copy(ability = best.first.defaultAbility)
        team.add(newMember)
        usedSpecies.add(best.first.displayName.lowercase())

        if (isLegendaryOrMythical(best.first)) legendaryMythicalCount++
        if (isStarter(best.first)) starterCount++
        if (isMega(best.first)) megaCount++
        if (isDynamax(best.first)) dynamaxCount++
    }

    return GeneratorResult(team = team)
}

/**
 * Regenerate a single slot in an existing proposed team. Picks randomly among the top 5 scoring
 * candidates. The pool excludes only Pokémon occupying the other five slots.
 */
fun regenerateSlot(
    chart: TypeChart,
    allPokemon: List<PokemonEntry>,
    currentTeam: List<TeamMember>,
    slotIndex: Int,
    constraints: GeneratorConstraints,
    random: Random = Random.Default,
): TeamMember {
    val otherMembers = currentTeam.filterIndexed { i, _ -> i != slotIndex }
    val pool = buildEligiblePool(allPokemon, constraints)
    val usedSpecies = otherMembers.mapTo(mutableSetOf()) { it.speciesName.lowercase() }

    var candidatePool = pool.filter { it.displayName.lowercase() !in usedSpecies }

    if (constraints.legendaryMythicalSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isLegendaryOrMythical(it) } == true }
        if (count >= constraints.legendaryMythicalSlots) candidatePool = candidatePool.filterNot { isLegendaryOrMythical(it) }
    }
    if (constraints.starterSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isStarter(it) } == true }
        if (count >= constraints.starterSlots) candidatePool = candidatePool.filterNot { isStarter(it) }
    }
    if (constraints.megaSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isMega(it) } == true }
        if (count >= constraints.megaSlots) candidatePool = candidatePool.filterNot { isMega(it) }
    }
    if (constraints.dynamaxSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isDynamax(it) } == true }
        if (count >= constraints.dynamaxSlots) candidatePool = candidatePool.filterNot { isDynamax(it) }
    }

    if (candidatePool.isEmpty()) {
        return currentTeam[slotIndex]
    }

    // computeScore adds fresh random noise per call, so it must be evaluated exactly once per
    // candidate here and sorted on the stored value — sortedByDescending { computeScore(...) }
    // re-invokes the selector on every comparison, which breaks Comparator's contract and makes
    // Collections.sort's TimSort throw once the pool is large enough to leave insertion-sort
    // territory (candidatePool is the full eligible catalogue here, not a test-sized fixture).
    val scored = candidatePool
        .map { entry ->
            val member = memberFromEntry(entry)
            Triple(entry, member, computeScore(chart, member, otherMembers, random))
        }
        .sortedByDescending { (_, _, score) -> score }

    val topN = minOf(5, scored.size)
    val picked = scored[random.nextInt(topN)]

    return picked.second.copy(ability = picked.first.defaultAbility)
}
