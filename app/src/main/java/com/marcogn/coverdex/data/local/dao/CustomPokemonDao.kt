package com.marcogn.coverdex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonWithMoves
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPokemonDao {

    @Transaction
    @Query("SELECT * FROM custom_pokemon ORDER BY createdAtEpochMillis")
    fun observeRoster(): Flow<List<CustomPokemonWithMoves>>

    @Query("SELECT EXISTS(SELECT 1 FROM custom_pokemon WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert
    suspend fun insert(entity: CustomPokemonEntity)

    @Update
    suspend fun update(entity: CustomPokemonEntity)

    @Query("DELETE FROM custom_pokemon_move WHERE customId = :customId")
    suspend fun deleteMovesForCustom(customId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoves(moves: List<CustomPokemonMoveEntity>)

    /** Same exists-check pattern as `TeamDao.upsertTeam` — see its doc. Moves are always fully
     * replaced in the same transaction, so a REPLACE-based parent upsert would technically be
     * safe here too, but checking `exists()` uniformly means a future partial-update path can
     * never reintroduce the cascade-wipe bug by forgetting to rewrite them. */
    @Transaction
    suspend fun upsert(entity: CustomPokemonEntity, moves: List<CustomPokemonMoveEntity>) {
        if (exists(entity.id)) update(entity) else insert(entity)
        deleteMovesForCustom(entity.id)
        insertMoves(moves)
    }

    @Query("DELETE FROM custom_pokemon WHERE id = :id")
    suspend fun delete(id: String)
}
