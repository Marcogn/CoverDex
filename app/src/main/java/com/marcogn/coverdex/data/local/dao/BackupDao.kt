package com.marcogn.coverdex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity

/**
 * Restore-only: the full-replace transaction a local backup import needs, spanning tables that
 * otherwise each have their own DAO — copied from Hall of Memories' own `BackupDao.kt`. Deleting
 * every team cascades to its members and their moves (`ON DELETE CASCADE`); deleting every custom
 * roster entry cascades to its moves the same way. Never touches `poke_*` tables — the cache is
 * never part of a backup (`docs/plan/phase-5-import-export-and-settings.md` §4).
 */
@Dao
interface BackupDao {

    @Query("DELETE FROM team")
    suspend fun deleteAllTeams()

    @Query("DELETE FROM custom_pokemon")
    suspend fun deleteAllCustomPokemon()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeams(teams: List<TeamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<TeamMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoves(moves: List<TeamMemberMoveEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPokemon(items: List<CustomPokemonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomMoves(moves: List<CustomPokemonMoveEntity>)

    /** Single-user, no merge: every existing team/member/move/custom-roster-entry is gone before
     * the restored ones land, all inside one transaction. */
    @Transaction
    suspend fun replaceAll(
        teams: List<TeamEntity>,
        members: List<TeamMemberEntity>,
        moves: List<TeamMemberMoveEntity>,
        customPokemon: List<CustomPokemonEntity>,
        customMoves: List<CustomPokemonMoveEntity>,
    ) {
        deleteAllTeams()
        deleteAllCustomPokemon()
        insertTeams(teams)
        insertMembers(members)
        insertMoves(moves)
        insertCustomPokemon(customPokemon)
        insertCustomMoves(customMoves)
    }
}
