package com.marcogn.coverdex.ui.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.common.EmptyState

/** The custom Pokémon roster — list, create, edit (tap a row), delete (its own icon button, no
 * confirmation: neither `legacy-web`'s `CustomPkmnPage` nor this phase's plan asks for one, unlike
 * the Teams screen's team deletion). */
@Composable
fun RosterScreen(
    onMenuClick: () -> Unit,
    onEntrySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RosterViewModel = hiltViewModel(),
) {
    val roster by viewModel.roster.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { CoverDexTopBar(title = stringResource(R.string.roster_title), onMenuClick = onMenuClick) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEntrySelected(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.roster_new_title))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (roster.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.roster_empty_title),
                    subtitle = stringResource(R.string.roster_empty_subtitle),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(roster, key = { it.id }) { member ->
                        RosterEntryCard(
                            member = member,
                            onClick = { onEntrySelected(member.id) },
                            onDelete = { viewModel.delete(member.id) },
                        )
                    }
                }
            }
        }
    }
}
