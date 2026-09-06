package com.marcogn.coverdex.domain.model

/**
 * A single Pokémon **form** from the cached catalogue — [id] is `pokemon.csv`'s `id`, which is
 * the form id, not the species id (327 of 1351 rows are forms with id > 10000: megas, regional
 * variants, etc.). [speciesId]/[speciesName] point at the species the form belongs to.
 *
 * [types] is a fixed pair, not a list — Pokémon have at most two types, and `second == null`
 * means single-typed, never an empty placeholder value.
 *
 * [isDefaultForm] is `pokemon.csv`'s `is_default` column — the species' primary/base
 * representative form (e.g. `zygarde-50` for species `zygarde`, not `zygarde-mega` or
 * `zygarde-10`). Not shown anywhere by itself; used to rank a species' base form ahead of its
 * alternate forms in search results.
 */
data class PokemonEntry(
    val id: Int,
    val name: String,
    val displayName: String,
    val speciesId: Int,
    val speciesName: String,
    val types: Pair<PokemonType, PokemonType?>,
    val isLegendary: Boolean,
    val isMythical: Boolean,
    val isFinalEvolution: Boolean,
    val generationIntroduced: Int,
    val defaultAbility: String?,
    val isDefaultForm: Boolean,
    /** Current-generation base stat total (sum of all six stats). `0` when the pinned dataset has
     * no `pokemon_stats` rows for this form — never negative, never null. Added in Phase 7 as the
     * suggestion ranking's tie-break, never its primary sort — see
     * docs/plan/phase-7-accuracy-and-customization.md §5. */
    val baseStatTotal: Int = 0,
)
