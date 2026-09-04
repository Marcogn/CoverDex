package com.marcogn.coverdex.ui.teams

import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.model.Team

data class TeamsUiState(
    val teams: List<Team> = emptyList(),
    val syncState: SyncState = SyncState.Idle,
)
