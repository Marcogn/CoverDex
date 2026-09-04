package com.marcogn.coverdex.ui.teams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.common.EmptyState

/**
 * The teams list — graph start destination. Empty state only in this phase; team creation, the
 * six-slot roster and everything else lands in Phase 2 (docs/plan/phase-2-teams-and-roster.md).
 * Kicks off the dataset sync on first load — see [TeamsViewModel] and [SyncBanner] — non-blocking:
 * the empty state below renders immediately regardless of sync progress.
 */
@Composable
fun TeamsScreen(
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamsViewModel = hiltViewModel(),
) {
    val syncState by viewModel.syncState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { CoverDexTopBar(title = stringResource(R.string.teams_title), onMenuClick = onMenuClick) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SyncBanner(syncState = syncState)
            EmptyState(
                title = stringResource(R.string.teams_empty_title),
                subtitle = stringResource(R.string.teams_empty_subtitle),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
