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
