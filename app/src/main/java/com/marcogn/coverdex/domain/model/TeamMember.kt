package com.marcogn.coverdex.domain.model

/**
 * A single team slot's Pokémon, or an entry in the custom roster — `legacy-web`'s
 * `src/types/index.ts` `TeamMember` shape (see `docs/plan/phase-2-teams-and-roster.md` §2).
 * [pokedexId] is nullable and carries no foreign key into the cache: [speciesName] and [types]
 * are denormalized snapshots, so wiping the cached catalogue must never alter or blank a saved
 * team. A custom Pokémon (typed by hand, or from the roster) simply has no [pokedexId] and no
 * sprite; a species picked from the cache keeps its id so the sprite resolver has one. [moves]
 * is always length 4, `null` for an empty slot — mirroring `TeamMember.moves`'s fixed-size
 * `(PokemonMove | null)[4]` in the TypeScript original.
 */
data class TeamMember(
    val id: String,
    val pokedexId: Int?,
    val speciesName: String,
    val types: Pair<PokemonType, PokemonType?>,
    val ability: String?,
    val moves: List<PokemonMove?>,
    val isCustomSaved: Boolean,
    /** Free text, same "type it or pick it" contract as [ability] — modelled effects exist only
     * for the defensive subset in `domain/item/ItemEffects.kt`. Added in Phase 7; `null` for
     * every member that predates it and for a suggestion candidate ([memberFromEntry] never sets
     * one). See docs/plan/phase-7-accuracy-and-customization.md §4. */
    val item: String? = null,
)
