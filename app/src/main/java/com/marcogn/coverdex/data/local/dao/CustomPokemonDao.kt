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

    /** A real column-list `UPDATE`, not `@Update` on the whole entity — `entity.createdAtEpochMillis`
     * is deliberately never part of it, so editing a roster entry can never reset its creation
     * time (and so its place in `observeRoster()`'s `ORDER BY createdAtEpochMillis`). */
    @Query(
        "UPDATE custom_pokemon SET name = :name, type1 = :type1, type2 = :type2, ability = :ability, " +
            "item = :item WHERE id = :id",
    )
    suspend fun updateFields(id: String, name: String, type1: String, type2: String?, ability: String?, item: String?)

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
        if (exists(entity.id)) {
            updateFields(entity.id, entity.name, entity.type1, entity.type2, entity.ability, entity.item)
        } else {
            insert(entity)
        }
        deleteMovesForCustom(entity.id)
        insertMoves(moves)
    }

    @Query("DELETE FROM custom_pokemon WHERE id = :id")
    suspend fun delete(id: String)
}
