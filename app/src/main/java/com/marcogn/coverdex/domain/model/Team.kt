package com.marcogn.coverdex.domain.model

/**
 * [members] is always length 6, `null` for an empty slot — an empty slot is a row that does not
 * exist in Room, but the domain model keeps `legacy-web`'s fixed-size `(TeamMember | null)[6]`
 * shape (`src/types/index.ts`) so slot identity is stable across edits and the position in the
 * list *is* the slot index; the repository maps between the two representations.
 */
data class Team(
    val id: String,
    val name: String,
    val members: List<TeamMember?>,
    val createdAtEpochMillis: Long,
)
