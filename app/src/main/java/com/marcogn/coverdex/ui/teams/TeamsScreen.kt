package com.marcogn.coverdex.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.common.EmptyState

/**
 * The teams list — graph start destination. Create (a name dialog), rename, delete (a
 * confirmation dialog) and tap-to-open, per `docs/plan/phase-2-teams-and-roster.md` §3. Kicks off
 * the dataset sync on first load — see [TeamsViewModel] and [SyncBanner] — non-blocking: the list
 * (or empty state) renders immediately regardless of sync progress.
 */
@Composable
fun TeamsScreen(
    onMenuClick: () -> Unit,
    onTeamSelected: (String) -> Unit,
    onSurpriseMeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingTeam by remember { mutableStateOf<Team?>(null) }
    var deletingTeam by remember { mutableStateOf<Team?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CoverDexTopBar(
                title = stringResource(R.string.teams_title),
                onMenuClick = onMenuClick,
                actions = {
                    IconButton(onClick = onSurpriseMeClick) {
                        Icon(Icons.Default.Casino, contentDescription = stringResource(R.string.surprise_me_button))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.teams_new_team_title))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SyncBanner(syncState = uiState.syncState)
            if (uiState.teams.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.teams_empty_title),
                    subtitle = stringResource(R.string.teams_empty_subtitle),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.teams, key = { it.id }) { team ->
                        TeamCard(
                            team = team,
                            onClick = { onTeamSelected(team.id) },
                            onRename = { renamingTeam = team },
                            onDelete = { deletingTeam = team },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        TeamNameDialog(
            title = stringResource(R.string.teams_new_team_title),
            initialName = "",
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createTeam(name, onCreated = onTeamSelected)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renamingTeam?.let { team ->
        TeamNameDialog(
            title = stringResource(R.string.teams_rename_team_title),
            initialName = team.name,
            onConfirm = { name ->
                renamingTeam = null
                viewModel.renameTeam(team.id, name)
            },
            onDismiss = { renamingTeam = null },
        )
    }

    deletingTeam?.let { team ->
        DeleteTeamDialog(
            teamName = team.name,
            onConfirm = {
                deletingTeam = null
                viewModel.deleteTeam(team.id)
            },
            onDismiss = { deletingTeam = null },
        )
    }
}
