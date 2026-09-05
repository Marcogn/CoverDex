package com.marcogn.coverdex.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.showdown.exportTeamToShowdown
import com.marcogn.coverdex.ui.importexport.ExportShowdownDialog
import com.marcogn.coverdex.ui.team.analysis.AnalysisScreen

private const val TAB_POKEMON = 0
private const val TAB_ANALYSIS = 1

/**
 * The team's own screen: two tabs, Pokémon (this file's six-slot grid) and Analysis
 * ([com.marcogn.coverdex.ui.team.analysis.AnalysisScreen], `docs/plan/phase-3-analysis.md`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    onBackClick: () -> Unit,
    onSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamDetailViewModel = hiltViewModel(),
) {
    val team by viewModel.team.collectAsState()
    val showMoves by viewModel.showMoves.collectAsState()
    var selectedTab by remember { mutableIntStateOf(TAB_POKEMON) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.teams_more_actions))
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_showdown_title)) },
                            onClick = {
                                showOverflowMenu = false
                                showExportDialog = true
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == TAB_POKEMON,
                    onClick = { selectedTab = TAB_POKEMON },
                    text = { Text(stringResource(R.string.team_detail_pokemon_tab)) },
                )
                Tab(
                    selected = selectedTab == TAB_ANALYSIS,
                    onClick = { selectedTab = TAB_ANALYSIS },
                    text = { Text(stringResource(R.string.team_detail_analysis_tab)) },
                )
            }

            when (selectedTab) {
                TAB_POKEMON -> {
                    val members = team?.members ?: List(6) { null }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.team_detail_enable_moves), modifier = Modifier.weight(1f))
                            Switch(checked = showMoves, onCheckedChange = viewModel::setShowMoves)
                        }
                        members.forEachIndexed { index, member ->
                            SlotSummaryCard(member = member, onClick = { onSlotClick(index) })
                        }
                    }
                }
                TAB_ANALYSIS -> {
                    AnalysisScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showExportDialog) {
        val current = team
        if (current != null) {
            ExportShowdownDialog(
                teamName = current.name,
                exportedText = exportTeamToShowdown(current.members),
                onDismiss = { showExportDialog = false },
            )
        }
    }
}
