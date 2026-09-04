package com.marcogn.coverdex.domain.model

import com.marcogn.coverdex.domain.pokeapi.SyncStage

/** The dataset sync's current state, observed by [com.marcogn.coverdex.data.pokeapi.DatasetSyncManager]. */
sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val stage: SyncStage, val progress: Float) : SyncState
    data class Success(val syncedAtEpochMillis: Long) : SyncState
    data class Failed(val message: String) : SyncState
}
