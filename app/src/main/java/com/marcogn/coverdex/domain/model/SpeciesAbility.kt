package com.marcogn.coverdex.domain.model

/**
 * One of a species form's canonical abilities — `pokemon_abilities.csv`, joined against
 * `abilities.csv`/`ability_names.csv` for display, per
 * docs/plan/phase-7-accuracy-and-customization.md §2.3/§3.2. [slot] `1`/`2` are the normal ability
 * slots, `3` the hidden ability; [isHidden] mirrors the CSV column directly rather than being
 * derived from [slot], since PokéAPI's own hidden flag is the authority.
 */
data class SpeciesAbility(
    val pokemonId: Int,
    val slug: String,
    val displayName: String,
    val isHidden: Boolean,
    val slot: Int,
)
