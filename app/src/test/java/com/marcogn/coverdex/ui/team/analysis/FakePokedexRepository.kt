package com.marcogn.coverdex.ui.team.analysis

import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.repository.PokedexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** A minimal `PokedexRepository` test double — [AnalysisViewModelTest] only needs a controllable
 * [cacheStatus]/[typeChart]; everything else is unused by the ViewModel under test and left as a
 * cheap no-op/empty implementation rather than pulled in from the real cache-backed repository. */
class FakePokedexRepository(
    private val chart: TypeChart,
    isUsable: Boolean = true,
    private val pool: List<PokemonEntry> = emptyList(),
) : PokedexRepository {

    override val cacheStatus: Flow<CacheStatus> = MutableStateFlow(
        CacheStatus(isUsable = isUsable, syncedAtEpochMillis = null, speciesCount = 0, moveCount = 0, datasetRevision = null),
    )
    override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle)

    override fun startSyncIfNeeded() = Unit
    override fun forceResync() = Unit
    override suspend fun wipeCache() = Unit
    override fun searchSpecies(query: String, limit: Int?): Flow<List<PokemonEntry>> = flowOf(emptyList())
    override fun searchMoves(query: String): Flow<List<MoveEntry>> = flowOf(emptyList())
    override fun searchAbilities(query: String): Flow<List<AbilityEntry>> = flowOf(emptyList())
    override suspend fun speciesById(id: Int): PokemonEntry? = pool.find { it.id == id }
    override suspend fun speciesByName(name: String): PokemonEntry? = pool.find { it.name == name }
    override suspend fun allSpecies(): List<PokemonEntry> = pool
    override suspend fun allMoves(): List<MoveEntry> = emptyList()
    override suspend fun typeChart(): TypeChart = chart
}
