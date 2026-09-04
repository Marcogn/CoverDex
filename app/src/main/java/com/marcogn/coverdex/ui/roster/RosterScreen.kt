package com.marcogn.coverdex.ui.roster

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.common.EmptyState

/**
 * The custom Pokémon roster. Empty state only in this phase; roster CRUD lands in Phase 2
 * (docs/plan/phase-2-teams-and-roster.md).
 */
@Composable
fun RosterScreen(onMenuClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { CoverDexTopBar(title = stringResource(R.string.roster_title), onMenuClick = onMenuClick) },
    ) { innerPadding ->
        EmptyState(
            title = stringResource(R.string.roster_empty_title),
            subtitle = stringResource(R.string.roster_empty_subtitle),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
