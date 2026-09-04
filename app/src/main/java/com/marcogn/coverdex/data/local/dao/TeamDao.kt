package com.marcogn.coverdex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity
import com.marcogn.coverdex.data.local.entity.TeamWithMembers
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {

    @Transaction
    @Query("SELECT * FROM team ORDER BY position, createdAtEpochMillis")
    fun observeTeams(): Flow<List<TeamWithMembers>>

    @Transaction
    @Query("SELECT * FROM team WHERE id = :id")
    fun observeTeam(id: String): Flow<TeamWithMembers?>

    @Query("SELECT EXISTS(SELECT 1 FROM team WHERE id = :id)")
    suspend fun teamExists(id: String): Boolean

    @Query("SELECT COUNT(*) FROM team")
    suspend fun countTeams(): Int

    @Insert
    suspend fun insertTeam(team: TeamEntity)

    @Update
    suspend fun updateTeam(team: TeamEntity)

    /**
     * `@Insert(onConflict = REPLACE)` here would delete-then-reinsert an existing row, cascading
     * through `team_member`'s `ON DELETE CASCADE` and wiping every member (and their moves) on
     * every rename — the exact bug Hall of Memories hit with `HackDao`. `updateTeam` never
     * deletes the row, so no cascade fires.
     */
    @Transaction
    suspend fun upsertTeam(team: TeamEntity) {
        if (teamExists(team.id)) updateTeam(team) else insertTeam(team)
    }

    @Query("DELETE FROM team WHERE id = :id")
    suspend fun deleteTeam(id: String)

    // --- Members ---

    @Query("DELETE FROM team_member WHERE teamId = :teamId AND slotIndex = :slotIndex")
    suspend fun deleteMemberAtSlot(teamId: String, slotIndex: Int)

    @Insert
    suspend fun insertMember(member: TeamMemberEntity)

    @Insert
    suspend fun insertMoves(moves: List<TeamMemberMoveEntity>)

    /**
     * A slot's member id changes every time a new species is picked (see
     * `docs/plan/phase-2-teams-and-roster.md` §2) and stays the same for an in-place edit
     * (ability, type override, a move); deleting by *slot* rather than by id handles both
     * uniformly, and its `ON DELETE CASCADE` clears the old member's moves for free before the
     * new ones are inserted. One transaction: the slot never observably holds a member with no
     * moves rewritten, or two members at once.
     */
    @Transaction
    suspend fun saveMember(teamId: String, slotIndex: Int, member: TeamMemberEntity, moves: List<TeamMemberMoveEntity>) {
        deleteMemberAtSlot(teamId, slotIndex)
        insertMember(member)
        insertMoves(moves)
    }

    @Transaction
    suspend fun clearSlot(teamId: String, slotIndex: Int) {
        deleteMemberAtSlot(teamId, slotIndex)
    }
}
