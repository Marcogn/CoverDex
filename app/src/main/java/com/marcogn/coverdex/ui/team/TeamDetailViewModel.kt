package com.marcogn.coverdex.ui.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.coverdex.data.settings.ThemePreferences
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.repository.TeamRepository
import com.marcogn.coverdex.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TeamDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    teamRepository: TeamRepository,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val teamId: String = savedStateHandle.toRoute<Destination.TeamDetail>().teamId

    val team: StateFlow<Team?> = teamRepository.team(teamId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /** Global, not per-team — see `data/settings/ThemePreferences.kt`'s `showMoves` doc. */
    val showMoves: StateFlow<Boolean> = themePreferences.showMoves.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setShowMoves(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setShowMoves(enabled) }
    }
}
