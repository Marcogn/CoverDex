package com.marcogn.coverdex.domain.repository

import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PastBst
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.SpeciesAbility
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

    /** Every cached move — used to resolve a pasted Showdown team's move lines in bulk (Phase 5),
     * the same one-shot-list shape as [allSpecies]. */
    suspend fun allMoves(): List<MoveEntry>

    suspend fun typeChart(): TypeChart

    /** A species form's canonical abilities (normal slots plus hidden, if any), ordered hidden
     * last — backs the ability picker's canonical list. Empty for a form with no
     * `pokemon_abilities` rows (see `DatasetAssembly.kt`'s `resolveDefaultAbility` doc) or for a
     * `pokemonId` not in the cache. Added in Phase 7, see
     * docs/plan/phase-7-accuracy-and-customization.md §3.2. */
    suspend fun abilitiesForSpecies(pokemonId: Int): List<SpeciesAbility>

    /** Every historical base-stat-total override, a small table (a few hundred rows at most) —
     * callers build a per-generation lookup from this, the same one-shot-list shape as
     * [allMoves]. A form absent here never had a historical change; its BST is
     * [PokemonEntry.baseStatTotal] at every generation. Added in Phase 7 for the suggestion
     * ranking's BST tie-break, see docs/plan/phase-7-accuracy-and-customization.md §2.2/§5.2. */
    suspend fun allPastBst(): List<PastBst>
}
