package com.marcogn.coverdex.domain.model

/**
 * A form's base stat total as it held through generation [generationId] — only emitted when it
 * differs from [PokemonEntry.baseStatTotal] (the current, latest-generation value), or for
 * generation 1, which is always emitted when a form has any historical Gen-1 data: Gen I has no
 * Special Attack/Special Defense split, so its canonical BST is the sum of **five** stats
 * (HP/Attack/Defense/Speed/Special), never six — a different scale from every later generation's
 * total, which must never be compared against it. See
 * docs/plan/phase-7-accuracy-and-customization.md §2.2 for the full derivation and worked
 * examples.
 */
data class PastBst(
    val pokemonId: Int,
    val generationId: Int,
    val bst: Int,
)

/**
 * Resolves a form's base stat total as of [generation] from the small set of historical
 * breakpoints in [pastBst] — `null` means "all generations", i.e. the current, latest-generation
 * value ([PokemonEntry.baseStatTotal]). For a real generation number, the answer is the BST at
 * the *smallest stored breakpoint >= [generation]*, or the current value if none exists: each
 * per-stat historical override is itself a step function that only changes at its own
 * breakpoints, so the combined total is provably constant between consecutive stored breakpoints
 * — the smallest one at or after [generation] always carries the correct value for every
 * generation in that interval. See docs/plan/phase-7-accuracy-and-customization.md §2.2/§5.2 for
 * the full derivation.
 *
 * Returns a resolver function rather than a single value so a suggestion pool of ~1351 entries is
 * resolved without re-filtering [pastBst] once per entry.
 */
fun bstResolverFor(pastBst: List<PastBst>, generation: Int?): (PokemonEntry) -> Int? {
    if (generation == null) return { it.baseStatTotal }
    val applicableByPokemonId: Map<Int, PastBst> = pastBst
        .filter { it.generationId >= generation }
        .groupBy { it.pokemonId }
        .mapValues { (_, rows) -> rows.minBy { it.generationId } }
    return { entry -> applicableByPokemonId[entry.id]?.bst ?: entry.baseStatTotal }
}
