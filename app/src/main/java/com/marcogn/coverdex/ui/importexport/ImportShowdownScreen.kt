package com.marcogn.coverdex.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.ui.common.PokemonSprite
import com.marcogn.coverdex.ui.common.TypeBadge
import com.marcogn.coverdex.ui.teams.TeamNameDialog
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Paste (or open a file), Parse, review what was resolved, then create a team from it —
 * `docs/plan/phase-5-import-export-and-settings.md` §2. An unresolved species is dropped and
 * listed as skipped; an unresolved move still imports as a placeholder the user must complete,
 * flagged per member rather than failing the whole paste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportShowdownScreen(
    onBackClick: () -> Unit,
    onTeamCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportShowdownViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
            if (text != null) viewModel.setInputText(text)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_showdown_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::setInputText,
                placeholder = { Text(stringResource(R.string.import_showdown_paste_placeholder)) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::parse, enabled = state.inputText.isNotBlank()) {
                    Text(stringResource(R.string.import_showdown_parse))
                }
                OutlinedButton(onClick = { openDocumentLauncher.launch(arrayOf("text/plain", "text/*")) }) {
                    Text(stringResource(R.string.import_showdown_open_file))
                }
            }

            val result = state.result
            if (result != null) {
                if (result.errors.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.import_showdown_skipped_species,
                            result.errors.joinToString(", ") { it.speciesName },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (result.members.isEmpty()) {
                    Text(stringResource(R.string.import_showdown_nothing_parsed), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.import_showdown_parsed_title), style = MaterialTheme.typography.titleSmall)
                    result.members.forEach { imported ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PokemonSprite(pokemonId = imported.member.pokedexId, context = SpriteContext.DROPDOWN, modifier = Modifier.size(32.dp))
                            Text(imported.member.speciesName, modifier = Modifier.weight(1f))
                            TypeBadge(imported.member.types.first, size = 16.dp)
                            imported.member.types.second?.let { TypeBadge(it, size = 16.dp) }
                            if (imported.unknownMoveNames.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.import_showdown_unknown_moves_count, imported.unknownMoveNames.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_showdown_create_team))
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
                viewModel.createTeam(name, onCreated = onTeamCreated)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}
