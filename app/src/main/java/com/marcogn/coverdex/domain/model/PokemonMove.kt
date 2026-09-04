package com.marcogn.coverdex.domain.model

/**
 * A move attached to a team slot or roster entry — a user-facing snapshot, not a reference into
 * the cached [MoveEntry] it may have been picked from; editing the cache must never alter a
 * saved team. [isCustom] marks a move the user typed that wasn't found in the cache; its [type],
 * [power] and [damageClass] are then free-form fields the editor exposes for the user to fill in
 * (see `docs/implementation-decisions.md`, "Phase 2", for why a new custom move defaults to
 * [PokemonType.NORMAL]/[DamageClass.PHYSICAL] rather than the phase plan's own paraphrase).
 */
data class PokemonMove(
    val id: String,
    val name: String,
    val type: PokemonType,
    val power: Int?,
    val damageClass: DamageClass,
    val isCustom: Boolean,
)
