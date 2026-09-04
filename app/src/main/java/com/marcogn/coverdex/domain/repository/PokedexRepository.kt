package com.marcogn.coverdex.domain.repository

import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.model.TypeChart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PokedexRepository {

    /** The persisted cache's status: usable, synced-at, counts, dataset revision. */
    val cacheStatus: Flow<CacheStatus>

    /** The live state of a sync run, if one is in progress. */
    val syncState: StateFlow<SyncState>

    /** No-op if the cache is already fresh for the current schema version and dataset revision;
     * otherwise runs in the background, never blocking the caller. */
    fun startSyncIfNeeded()

    /** Re-syncs regardless of freshness, in the background. */
    fun forceResync()

    /** Wipes the cache. Never touches user data — there is none in Phase 1, and from Phase 2
     * onward this must still name the cache tables explicitly rather than clearAllTables(). */
    suspend fun wipeCache()

    /** Blank [query] returns empty, not everything — see docs/plan/native-spec.md, "Searchable
     * dropdowns". `limit = null` returns every match, no cap, no pagination. */
    fun searchSpecies(query: String, limit: Int? = null): Flow<List<PokemonEntry>>
    fun searchMoves(query: String): Flow<List<MoveEntry>>
    fun searchAbilities(query: String): Flow<List<AbilityEntry>>

    suspend fun speciesById(id: Int): PokemonEntry?
    suspend fun speciesByName(name: String): PokemonEntry?

    /** The full pool — used by the suggestion engine and team generator from Phase 4 onward. */
    suspend fun allSpecies(): List<PokemonEntry>

    suspend fun typeChart(): TypeChart
}
