package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.dao.PokedexDao
import com.marcogn.coverdex.data.pokeapi.DATASET_REVISION
import com.marcogn.coverdex.data.pokeapi.DatasetSyncManager
import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.pokeapi.DATASET_SCHEMA_VERSION
import com.marcogn.coverdex.domain.pokeapi.searchKey
import com.marcogn.coverdex.domain.repository.PokedexRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val NO_LIMIT = -1

@Singleton
class PokedexRepositoryImpl @Inject constructor(
    private val pokedexDao: PokedexDao,
    private val syncManager: DatasetSyncManager,
) : PokedexRepository {

    override val cacheStatus: Flow<CacheStatus> = pokedexDao.observeMeta().map { meta ->
        CacheStatus(
            isUsable = meta != null && meta.schemaVersion == DATASET_SCHEMA_VERSION && meta.datasetRevision == DATASET_REVISION,
            syncedAtEpochMillis = meta?.syncedAtEpochMillis,
            speciesCount = meta?.speciesCount ?: 0,
            moveCount = meta?.moveCount ?: 0,
            datasetRevision = meta?.datasetRevision,
        )
    }

    override val syncState: StateFlow<SyncState> = syncManager.state

    override fun startSyncIfNeeded() = syncManager.startIfNeeded()

    override fun forceResync() = syncManager.forceResync()

    override suspend fun wipeCache() = pokedexDao.clearCache()

    override fun searchSpecies(query: String, limit: Int?): Flow<List<PokemonEntry>> {
        val key = searchKey(query)
        if (key.isBlank()) return flowOf(emptyList())
        return pokedexDao.searchSpecies(key, limit ?: NO_LIMIT).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    override fun searchMoves(query: String): Flow<List<MoveEntry>> {
        val key = searchKey(query)
        if (key.isBlank()) return flowOf(emptyList())
        return pokedexDao.searchMoves(key, NO_LIMIT).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    override fun searchAbilities(query: String): Flow<List<AbilityEntry>> {
        val key = searchKey(query)
        if (key.isBlank()) return flowOf(emptyList())
        return pokedexDao.searchAbilities(key, NO_LIMIT).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun speciesById(id: Int): PokemonEntry? = pokedexDao.getSpeciesById(id)?.toDomain()

    override suspend fun speciesByName(name: String): PokemonEntry? =
        pokedexDao.getSpeciesBySearchName(searchKey(name))?.toDomain()

    override suspend fun allSpecies(): List<PokemonEntry> = pokedexDao.getAllSpecies().mapNotNull { it.toDomain() }

    override suspend fun allMoves(): List<MoveEntry> = pokedexDao.getAllMoves().mapNotNull { it.toDomain() }

    override suspend fun typeChart(): TypeChart = pokedexDao.getAllTypeEfficacy().toTypeChart()
}
