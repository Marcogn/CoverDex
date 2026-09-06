package com.marcogn.coverdex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.marcogn.coverdex.data.local.entity.PokeAbilityEntity
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.data.local.entity.PokeMoveEntity
import com.marcogn.coverdex.data.local.entity.PokePokemonAbilityEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesBstPastEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity
import com.marcogn.coverdex.data.local.entity.TypeEfficacyEntity
import kotlinx.coroutines.flow.Flow

/**
 * The cached catalogue. Search queries take a pre-normalized `key` (see
 * `domain/pokeapi/searchKey`) and `limit` — pass `-1` for "no cap", which SQLite's `LIMIT`
 * treats as unlimited, so both the capped and uncapped cases in
 * `domain.repository.PokedexRepository` are one query, not two.
 *
 * Ranking: a prefix match on the normalized name ranks before a contains-only match, and within
 * a tie a species' default form (`isDefaultForm`) ranks before its alternate forms — so searching
 * "zygarde" surfaces the base form before `zygarde-mega`.
 */
@Dao
interface PokedexDao {

    // --- Species ---

    @Query("DELETE FROM poke_species")
    suspend fun deleteAllSpecies()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecies(items: List<PokeSpeciesEntity>)

    @Transaction
    suspend fun replaceAllSpecies(items: List<PokeSpeciesEntity>) {
        deleteAllSpecies()
        insertSpecies(items)
    }

    @Query(
        "SELECT * FROM poke_species WHERE searchName LIKE '%' || :key || '%' " +
            "ORDER BY (CASE WHEN searchName LIKE :key || '%' THEN 0 ELSE 1 END), " +
            "(CASE WHEN isDefaultForm THEN 0 ELSE 1 END), LENGTH(name), id " +
            "LIMIT :limit",
    )
    fun searchSpecies(key: String, limit: Int): Flow<List<PokeSpeciesEntity>>

    @Query("SELECT * FROM poke_species WHERE id = :id")
    suspend fun getSpeciesById(id: Int): PokeSpeciesEntity?

    @Query("SELECT * FROM poke_species WHERE searchName = :key LIMIT 1")
    suspend fun getSpeciesBySearchName(key: String): PokeSpeciesEntity?

    @Query("SELECT * FROM poke_species")
    suspend fun getAllSpecies(): List<PokeSpeciesEntity>

    // --- Moves ---

    @Query("DELETE FROM poke_move")
    suspend fun deleteAllMoves()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoves(items: List<PokeMoveEntity>)

    @Transaction
    suspend fun replaceAllMoves(items: List<PokeMoveEntity>) {
        deleteAllMoves()
        insertMoves(items)
    }

    @Query(
        "SELECT * FROM poke_move WHERE searchName LIKE '%' || :key || '%' " +
            "ORDER BY (CASE WHEN searchName LIKE :key || '%' THEN 0 ELSE 1 END), LENGTH(name), id " +
            "LIMIT :limit",
    )
    fun searchMoves(key: String, limit: Int): Flow<List<PokeMoveEntity>>

    @Query("SELECT * FROM poke_move")
    suspend fun getAllMoves(): List<PokeMoveEntity>

    // --- Abilities ---

    @Query("DELETE FROM poke_ability")
    suspend fun deleteAllAbilities()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbilities(items: List<PokeAbilityEntity>)

    @Transaction
    suspend fun replaceAllAbilities(items: List<PokeAbilityEntity>) {
        deleteAllAbilities()
        insertAbilities(items)
    }

    @Query(
        "SELECT * FROM poke_ability WHERE searchName LIKE '%' || :key || '%' " +
            "ORDER BY (CASE WHEN searchName LIKE :key || '%' THEN 0 ELSE 1 END), LENGTH(name), id " +
            "LIMIT :limit",
    )
    fun searchAbilities(key: String, limit: Int): Flow<List<PokeAbilityEntity>>

    // --- Type efficacy ---

    @Query("DELETE FROM type_efficacy")
    suspend fun deleteAllTypeEfficacy()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTypeEfficacy(items: List<TypeEfficacyEntity>)

    @Transaction
    suspend fun replaceAllTypeEfficacy(items: List<TypeEfficacyEntity>) {
        deleteAllTypeEfficacy()
        insertTypeEfficacy(items)
    }

    @Query("SELECT * FROM type_efficacy")
    suspend fun getAllTypeEfficacy(): List<TypeEfficacyEntity>

    // --- Per-form canonical abilities (Phase 7) ---

    @Query("DELETE FROM poke_pokemon_ability")
    suspend fun deleteAllPokemonAbilities()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPokemonAbilities(items: List<PokePokemonAbilityEntity>)

    @Transaction
    suspend fun replaceAllPokemonAbilities(items: List<PokePokemonAbilityEntity>) {
        deleteAllPokemonAbilities()
        insertPokemonAbilities(items)
    }

    /** Ordered hidden-last, then by slot — matches the ability picker's canonical-list order
     * (phase-7-accuracy-and-customization.md §3.2). */
    @Query("SELECT * FROM poke_pokemon_ability WHERE pokemonId = :pokemonId ORDER BY isHidden, slot")
    suspend fun getAbilitiesForSpecies(pokemonId: Int): List<PokePokemonAbilityEntity>

    // --- Historical base stat totals (Phase 7) ---

    @Query("DELETE FROM poke_species_bst_past")
    suspend fun deleteAllBstPast()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBstPast(items: List<PokeSpeciesBstPastEntity>)

    @Transaction
    suspend fun replaceAllBstPast(items: List<PokeSpeciesBstPastEntity>) {
        deleteAllBstPast()
        insertBstPast(items)
    }

    /** Every historical BST row — a small table (a few hundred rows at most; see
     * phase-7-accuracy-and-customization.md §2.2), so callers building a per-generation lookup
     * load it whole, the same one-shot-list shape as [getAllSpecies]/[getAllMoves]. */
    @Query("SELECT * FROM poke_species_bst_past")
    suspend fun getAllBstPast(): List<PokeSpeciesBstPastEntity>

    // --- Cache metadata ---

    @Query("SELECT * FROM poke_cache_meta WHERE id = 1")
    fun observeMeta(): Flow<PokeCacheMetaEntity?>

    @Query("SELECT * FROM poke_cache_meta WHERE id = 1")
    suspend fun getMeta(): PokeCacheMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: PokeCacheMetaEntity)

    @Query("DELETE FROM poke_cache_meta")
    suspend fun deleteMeta()

    /** Writes the whole cache — every table above plus the meta row — in one transaction: all of
     * it or none of it. A half-written cache that reports itself synced is worse than no cache. */
    @Transaction
    suspend fun replaceCache(
        species: List<PokeSpeciesEntity>,
        moves: List<PokeMoveEntity>,
        abilities: List<PokeAbilityEntity>,
        typeEfficacy: List<TypeEfficacyEntity>,
        pokemonAbilities: List<PokePokemonAbilityEntity>,
        bstPast: List<PokeSpeciesBstPastEntity>,
        meta: PokeCacheMetaEntity,
    ) {
        replaceAllSpecies(species)
        replaceAllMoves(moves)
        replaceAllAbilities(abilities)
        replaceAllTypeEfficacy(typeEfficacy)
        replaceAllPokemonAbilities(pokemonAbilities)
        replaceAllBstPast(bstPast)
        upsertMeta(meta)
    }

    /** Wipes every cache table by name, explicitly. `clearAllTables()` must never appear in this
     * codebase — once Phase 2 adds team/roster tables it would take those with it too. */
    @Transaction
    suspend fun clearCache() {
        deleteAllSpecies()
        deleteAllMoves()
        deleteAllAbilities()
        deleteAllTypeEfficacy()
        deleteAllPokemonAbilities()
        deleteAllBstPast()
        deleteMeta()
    }
}
