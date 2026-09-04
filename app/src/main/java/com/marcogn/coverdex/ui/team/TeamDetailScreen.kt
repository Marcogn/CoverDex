package com.marcogn.coverdex.ui.team

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.ui.common.EmptyState

/**
 * The team's own screen: name in the top bar, back navigation. Slot editor and the Analysis tab
 * land in a later commit of this same phase (`docs/plan/phase-2-teams-and-roster.md` §3) — this
 * placeholder only exists so [com.marcogn.coverdex.ui.teams.TeamsScreen]'s "tap to open" and
 * "create then open" have somewhere real to navigate to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamDetailViewModel = hiltViewModel(),
) {
    val team by viewModel.team.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(team?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            )
        },
    ) { innerPadding ->
        EmptyState(
            title = stringResource(R.string.team_detail_placeholder_title),
            subtitle = stringResource(R.string.team_detail_placeholder_subtitle),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
