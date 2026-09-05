package com.marcogn.coverdex.domain.generator

import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.suggestion.TeamScoringContext
import com.marcogn.coverdex.domain.suggestion.computeCompositeScore
import com.marcogn.coverdex.domain.suggestion.memberFromEntry
import com.marcogn.coverdex.domain.suggestion.teamScoringContext
import kotlin.random.Random

/**
 * A direct port of `legacy-web/src/hooks/teamGenerator.ts`. Randomness is injectable — a
 * [Random] parameter, defaulting to [Random.Default] — rather than a direct `Math.random()` call,
 * so tests can seed it; see `docs/plan/phase-4-suggestions-and-generator.md` §3.
 *
 * `customs` is a deliberate native addition, not a port: `teamGenerator.ts` accepted a `customs:
 * TeamMember[]` parameter but never referenced it in any of its three function bodies, and Phase 4
 * dropped it here as genuinely dead. `GeneratorConstraints.customSlots` was kept anyway as a ported
 * struct field, which meant the Surprise Me screen shipped a "Custom slots" stepper that consumed
 * the six-slot budget and placed nothing — see `docs/post-migration-review.md`, finding 5, and
 * `docs/implementation-decisions.md`, "Post-migration review". `generateTeam` and `regenerateSlot`
 * now take `customs` (default `emptyList()`, so every existing call site is unaffected) and honour
 * `customSlots` as a reserved category exactly like starter/legendary-mythical/Mega/Dynamax, except
 * a custom is never chosen opportunistically in a free slot the way a catalogue Pokémon can be
 * once its own quota is met — customs live outside [buildEligiblePool]'s catalogue-only pool, so a
 * custom appears only while its own reserved budget still has room.
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

/** A generator candidate: either a catalogue entry (scored via [memberFromEntry], its ability
 * taken from [PokemonEntry.defaultAbility]) or a custom roster [TeamMember] (its own ability kept
 * as-is). [entry] is `null` for a custom — that is the single source of truth this file uses to
 * tell the two apart, since a custom is never legendary/mythical, a starter, a Mega or a Dynamax. */
private data class Candidate(val member: TeamMember, val entry: PokemonEntry?, val ability: String?)

private fun candidateFromEntry(entry: PokemonEntry): Candidate = Candidate(memberFromEntry(entry), entry, entry.defaultAbility)
private fun candidateFromCustom(member: TeamMember): Candidate = Candidate(member, null, member.ability)

/** Composite score for [candidate] against a team summarized by [context] (see
 * [teamScoringContext]), plus a small random tie-breaking factor. Ports `teamGenerator.ts`'s own
 * `computeScore` as a thin wrapper over the shared [computeCompositeScore] — [context]'s
 * `baseCoverage` doubles as both the "other members" coverage and the gain baseline, since the
 * generator (unlike the suggestion engine's replacement mode) never excludes a member from the
 * comparison, so building [context] from the exact team being scored against makes the two the
 * same set. Callers build [context] once per team (not once per candidate) — see
 * `docs/post-migration-review.md`, finding 3. */
private fun computeScore(chart: TypeChart, candidate: TeamMember, context: TeamScoringContext, random: Random): Double {
    val result = computeCompositeScore(chart, candidate, context, context.baseCoverage)
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
    customs: List<TeamMember> = emptyList(),
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
    var customCount = team.count { it.isCustomSaved }

    var starterSlotsRemaining = maxOf(0, constraints.starterSlots - starterCount)
    var legendaryMythicalSlotsRemaining = maxOf(0, constraints.legendaryMythicalSlots - legendaryMythicalCount)
    var megaSlotsRemaining = maxOf(0, constraints.megaSlots - megaCount)
    var dynamaxSlotsRemaining = maxOf(0, constraints.dynamaxSlots - dynamaxCount)
    var customSlotsRemaining = maxOf(0, constraints.customSlots - customCount)

    repeat(slotsToFill) {
        val candidates: List<Candidate>

        if (legendaryMythicalSlotsRemaining > 0) {
            candidates = pool.filter { isLegendaryOrMythical(it) && it.displayName.lowercase() !in usedSpecies }.map(::candidateFromEntry)
            legendaryMythicalSlotsRemaining--
        } else if (starterSlotsRemaining > 0) {
            candidates = pool.filter { isStarter(it) && it.displayName.lowercase() !in usedSpecies }.map(::candidateFromEntry)
            starterSlotsRemaining--
        } else if (customSlotsRemaining > 0) {
            candidates = customs.filter { it.speciesName.lowercase() !in usedSpecies }.map(::candidateFromCustom)
            customSlotsRemaining--
        } else if (megaSlotsRemaining > 0) {
            candidates = pool.filter { isMega(it) && it.displayName.lowercase() !in usedSpecies }.map(::candidateFromEntry)
            megaSlotsRemaining--
        } else if (dynamaxSlotsRemaining > 0) {
            candidates = pool.filter { isDynamax(it) && it.displayName.lowercase() !in usedSpecies }.map(::candidateFromEntry)
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
            candidates = free.map(::candidateFromEntry)
        }

        if (candidates.isEmpty()) {
            return GeneratorResult(team = team, warning = "tooFewPokemon")
        }

        // Built once per slot, not once per candidate — see docs/post-migration-review.md,
        // finding 3.
        val context = teamScoringContext(chart, team)
        // maxByOrNull calls its selector exactly once per element (never per comparison), so this
        // is safe even though computeScore adds fresh random noise per call — see regenerateSlot's
        // own note below on the sort that must not do the same thing the same way.
        val best = candidates.maxByOrNull { candidate -> computeScore(chart, candidate.member, context, random) }!!

        team.add(best.member.copy(ability = best.ability))
        usedSpecies.add(best.member.speciesName.lowercase())

        if (best.entry != null) {
            if (isLegendaryOrMythical(best.entry)) legendaryMythicalCount++
            if (isStarter(best.entry)) starterCount++
            if (isMega(best.entry)) megaCount++
            if (isDynamax(best.entry)) dynamaxCount++
        } else {
            customCount++
        }
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
    customs: List<TeamMember> = emptyList(),
): TeamMember {
    val otherMembers = currentTeam.filterIndexed { i, _ -> i != slotIndex }
    val pool = buildEligiblePool(allPokemon, constraints)
    val usedSpecies = otherMembers.mapTo(mutableSetOf()) { it.speciesName.lowercase() }

    var entryPool = pool.filter { it.displayName.lowercase() !in usedSpecies }

    if (constraints.legendaryMythicalSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isLegendaryOrMythical(it) } == true }
        if (count >= constraints.legendaryMythicalSlots) entryPool = entryPool.filterNot { isLegendaryOrMythical(it) }
    }
    if (constraints.starterSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isStarter(it) } == true }
        if (count >= constraints.starterSlots) entryPool = entryPool.filterNot { isStarter(it) }
    }
    if (constraints.megaSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isMega(it) } == true }
        if (count >= constraints.megaSlots) entryPool = entryPool.filterNot { isMega(it) }
    }
    if (constraints.dynamaxSlots > 0) {
        val count = otherMembers.count { m -> findEntry(allPokemon, m)?.let { isDynamax(it) } == true }
        if (count >= constraints.dynamaxSlots) entryPool = entryPool.filterNot { isDynamax(it) }
    }

    // A custom is a regeneration candidate only while its own reserved budget still has room —
    // same rule generateTeam applies, since customs sit outside buildEligiblePool's catalogue-only
    // pool and are never picked opportunistically the way a catalogue Pokémon can be once its
    // quota is met (see the class doc above).
    val customPool: List<TeamMember> = if (constraints.customSlots > 0) {
        val count = otherMembers.count { it.isCustomSaved }
        if (count >= constraints.customSlots) emptyList() else customs.filter { it.speciesName.lowercase() !in usedSpecies }
    } else {
        emptyList()
    }

    val candidatePool = entryPool.map(::candidateFromEntry) + customPool.map(::candidateFromCustom)

    if (candidatePool.isEmpty()) {
        return currentTeam[slotIndex]
    }

    // Built once for every candidate below, not once per candidate — see
    // docs/post-migration-review.md, finding 3.
    val context = teamScoringContext(chart, otherMembers)

    // computeScore adds fresh random noise per call, so it must be evaluated exactly once per
    // candidate here and sorted on the stored value — sortedByDescending { computeScore(...) }
    // re-invokes the selector on every comparison, which breaks Comparator's contract and makes
    // Collections.sort's TimSort throw once the pool is large enough to leave insertion-sort
    // territory (candidatePool is the full eligible catalogue here, not a test-sized fixture).
    val scored = candidatePool
        .map { candidate -> candidate to computeScore(chart, candidate.member, context, random) }
        .sortedByDescending { (_, score) -> score }

    val topN = minOf(5, scored.size)
    val picked = scored[random.nextInt(topN)].first

    return picked.member.copy(ability = picked.ability)
}
