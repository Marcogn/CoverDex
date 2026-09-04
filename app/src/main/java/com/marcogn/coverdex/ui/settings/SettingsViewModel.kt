package com.marcogn.coverdex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.repository.PokedexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val EMPTY_CACHE_STATUS = CacheStatus(isUsable = false, syncedAtEpochMillis = null, speciesCount = 0, moveCount = 0, datasetRevision = null)

data class SettingsUiState(
    val cacheStatus: CacheStatus = EMPTY_CACHE_STATUS,
    val syncState: SyncState = SyncState.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pokedexRepository: PokedexRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        pokedexRepository.cacheStatus,
        pokedexRepository.syncState,
    ) { cacheStatus, syncState -> SettingsUiState(cacheStatus, syncState) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun syncNow() = pokedexRepository.forceResync()

    fun clearCache() {
        viewModelScope.launch { pokedexRepository.wipeCache() }
    }
}
