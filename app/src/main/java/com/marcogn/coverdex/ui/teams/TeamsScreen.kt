package com.marcogn.coverdex.ui.teams

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.common.EmptyState

/**
 * The teams list — graph start destination. Empty state only in this phase; team creation, the
 * six-slot roster and everything else lands in Phase 2 (docs/plan/phase-2-teams-and-roster.md).
 */
@Composable
fun TeamsScreen(onMenuClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { CoverDexTopBar(title = stringResource(R.string.teams_title), onMenuClick = onMenuClick) },
    ) { innerPadding ->
        EmptyState(
            title = stringResource(R.string.teams_empty_title),
            subtitle = stringResource(R.string.teams_empty_subtitle),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
