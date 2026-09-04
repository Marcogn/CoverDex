package com.marcogn.coverdex.domain.model

/**
 * A single Pokémon **form** from the cached catalogue — [id] is `pokemon.csv`'s `id`, which is
 * the form id, not the species id (327 of 1351 rows are forms with id > 10000: megas, regional
 * variants, etc.). [speciesId]/[speciesName] point at the species the form belongs to.
 *
 * [types] is a fixed pair, not a list — Pokémon have at most two types, and `second == null`
 * means single-typed, never an empty placeholder value.
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
)
