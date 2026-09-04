package com.marcogn.coverdex.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TeamsViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val pokedexRepository: PokedexRepository,
) : ViewModel() {

    val uiState: StateFlow<TeamsUiState> = combine(
        teamRepository.teams,
        pokedexRepository.syncState,
    ) { teams, syncState -> TeamsUiState(teams, syncState) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TeamsUiState(),
        )

    init {
        // Non-blocking: the Teams screen renders immediately regardless. See ui/teams/SyncBanner.kt.
        pokedexRepository.startSyncIfNeeded()
    }

    /** Creates the team, then hands its new id to [onCreated] — the screen navigates straight
     * into it, matching `legacy-web`'s `createEmptyTeam` (create and immediately open). */
    fun createTeam(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = teamRepository.createTeam(name)
            onCreated(id)
        }
    }

    fun renameTeam(id: String, name: String) {
        viewModelScope.launch { teamRepository.renameTeam(id, name) }
    }

    fun deleteTeam(id: String) {
        viewModelScope.launch { teamRepository.deleteTeam(id) }
    }
}
