package com.marcogn.coverdex.data.pokeapi

import android.util.Log
import com.marcogn.coverdex.data.local.dao.PokedexDao
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.data.repository.toEntities
import com.marcogn.coverdex.data.repository.toEntity
import com.marcogn.coverdex.di.ApplicationScope
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.pokeapi.DATASET_SCHEMA_VERSION
import com.marcogn.coverdex.domain.pokeapi.SyncStage
import com.marcogn.coverdex.domain.pokeapi.assembleDataset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

private const val TAG = "DatasetSyncManager"

/**
 * Orchestrates the dataset sync: fetch the 8 pinned CSVs, assemble them, write the whole cache in
 * one Room transaction. Runs on an application-scoped coroutine so it survives navigation and
 * configuration changes — no WorkManager, which would be a new dependency for a one-shot
 * foreground task that finishes in a couple of seconds (see docs/plan/reference-pokedata.md §2).
 *
 * Never blocks the UI behind a full-screen loader — that is the whole point of this phase (see
 * docs/plan/phase-1-dataset-sync.md, "never blocks the UI"). Callers observe [state] and render
 * whatever inline progress they want; nothing here forces a blocking screen.
 */
@Singleton
class DatasetSyncManager @Inject constructor(
    private val client: DatasetSource,
    private val pokedexDao: PokedexDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** No-op if a sync is already running or the cache is already fresh for the current schema
     * version and pinned dataset revision. */
    fun startIfNeeded() {
        scope.launch { runSync(force = false) }
    }

    /** Wipes the cache and re-syncs regardless of freshness. */
    fun forceResync() {
        scope.launch { runSync(force = true) }
    }

    suspend fun isCacheFresh(): Boolean {
        val meta = pokedexDao.getMeta() ?: return false
        return meta.schemaVersion == DATASET_SCHEMA_VERSION && meta.datasetRevision == DATASET_REVISION
    }

    private suspend fun runSync(force: Boolean) {
        if (!mutex.tryLock()) return
        try {
            if (!force && isCacheFresh()) {
                _state.value = SyncState.Success(pokedexDao.getMeta()!!.syncedAtEpochMillis)
                return
            }

            _state.value = SyncState.Running(SyncStage.DOWNLOADING, progress = 0f)
            val files = client.fetchAll()

            _state.value = SyncState.Running(SyncStage.PARSING, progress = 0.8f)
            val dataset = assembleDataset(
                pokemonCsv = files.getValue(DatasetFile.POKEMON),
                speciesCsv = files.getValue(DatasetFile.SPECIES),
                pokemonTypesCsv = files.getValue(DatasetFile.POKEMON_TYPES),
                pokemonAbilitiesCsv = files.getValue(DatasetFile.POKEMON_ABILITIES),
                abilitiesCsv = files.getValue(DatasetFile.ABILITIES),
                movesCsv = files.getValue(DatasetFile.MOVES),
                typesCsv = files.getValue(DatasetFile.TYPES),
                typeEfficacyCsv = files.getValue(DatasetFile.TYPE_EFFICACY),
                pokemonStatsCsv = files.getValue(DatasetFile.POKEMON_STATS),
                pokemonStatsPastCsv = files.getValue(DatasetFile.POKEMON_STATS_PAST),
                abilityNamesCsv = files.getValue(DatasetFile.ABILITY_NAMES),
                moveNamesCsv = files.getValue(DatasetFile.MOVE_NAMES),
            )

            _state.value = SyncState.Running(SyncStage.WRITING, progress = 0.95f)
            val syncedAt = System.currentTimeMillis()
            pokedexDao.replaceCache(
                species = dataset.species.map { it.toEntity() },
                moves = dataset.moves.map { it.toEntity() },
                abilities = dataset.abilities.map { it.toEntity() },
                typeEfficacy = dataset.typeChart.toEntities(),
                pokemonAbilities = dataset.pokemonAbilities.map { it.toEntity() },
                bstPast = dataset.pastBst.map { it.toEntity() },
                meta = PokeCacheMetaEntity(
                    schemaVersion = DATASET_SCHEMA_VERSION,
                    datasetRevision = DATASET_REVISION,
                    syncedAtEpochMillis = syncedAt,
                    speciesCount = dataset.species.size,
                    moveCount = dataset.moves.size,
                ),
            )
            _state.value = SyncState.Success(syncedAt)
        } catch (e: Exception) {
            // The real cause goes into the state, not a generic message — see CLAUDE.md's note
            // on ThePatientGamerHelper's TheGamesDB search having made exactly that mistake once.
            Log.w(TAG, "Dataset sync failed", e)
            _state.value = SyncState.Failed(e.message ?: e.toString())
        } finally {
            mutex.unlock()
        }
    }
}
