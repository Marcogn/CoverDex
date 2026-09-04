package com.marcogn.coverdex.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.repository.PokedexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TeamsViewModel @Inject constructor(
    pokedexRepository: PokedexRepository,
) : ViewModel() {

    val syncState: StateFlow<SyncState> = pokedexRepository.syncState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncState.Idle,
    )

    init {
        // Non-blocking: the Teams screen renders its empty state immediately regardless. See
        // ui/teams/SyncBanner.kt.
        pokedexRepository.startSyncIfNeeded()
    }
}
