package com.marcogn.coverdex.domain.repository

import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.model.TeamMember
import kotlinx.coroutines.flow.Flow

interface TeamRepository {

    val teams: Flow<List<Team>>

    fun team(id: String): Flow<Team?>

    /** Ids are generated here, never in the ViewModel or the DAO. Returns the new team's id. */
    suspend fun createTeam(name: String): String

    suspend fun renameTeam(id: String, name: String)

    suspend fun deleteTeam(id: String)

    /** Replaces the slot's Pokémon and all four of its moves in one transaction — never a
     * partial write that could leave a slot with a member but the previous member's moves. */
    suspend fun saveMember(teamId: String, slotIndex: Int, member: TeamMember)

    suspend fun clearSlot(teamId: String, slotIndex: Int)
}
