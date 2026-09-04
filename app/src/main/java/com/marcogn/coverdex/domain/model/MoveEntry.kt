package com.marcogn.coverdex.domain.model

/** A single move from the cached catalogue. [power] is `null` for status moves and for moves
 * whose power is variable/fixed-damage (OHKO moves, etc.) — never coerced to 0. */
data class MoveEntry(
    val id: Int,
    val name: String,
    val displayName: String,
    val type: PokemonType,
    val power: Int?,
    val damageClass: DamageClass,
)
