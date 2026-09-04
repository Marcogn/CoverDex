package com.marcogn.coverdex.ui.roster

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.ui.common.EditableComboBox
import com.marcogn.coverdex.ui.common.TypeDropdown
import com.marcogn.coverdex.ui.team.MoveSlotEditor
import java.util.UUID

private const val MOVE_COUNT = 4

private data class RosterDraft(
    val id: String,
    val speciesName: String,
    val type1: PokemonType,
    val type2: PokemonType?,
    val ability: String?,
    val moves: List<PokemonMove?>,
) {
    fun toTeamMember(): TeamMember = TeamMember(
        id = id,
        pokedexId = null,
        speciesName = speciesName,
        types = type1 to type2,
        ability = ability,
        moves = moves,
        isCustomSaved = true,
    )

    companion object {
        fun blank(): RosterDraft = RosterDraft(
            id = UUID.randomUUID().toString(),
            speciesName = "",
            type1 = PokemonType.NORMAL,
            type2 = null,
            ability = null,
            moves = List(MOVE_COUNT) { null },
        )

        fun from(member: TeamMember): RosterDraft = RosterDraft(
            id = member.id,
            speciesName = member.speciesName,
            type1 = member.types.first,
            type2 = member.types.second,
            ability = member.ability,
            moves = member.moves,
        )
    }
}

/**
 * The custom roster's editor — "the same editor as a slot, minus the species picker" per
 * `phase-2-teams-and-roster.md` §3: a free-text name instead of [com.marcogn.coverdex.ui.common.SearchableDropdown],
 * everything else (type overrides, ability, moves) shared with [com.marcogn.coverdex.ui.team.SlotEditorScreen]
 * via [MoveSlotEditor]/[TypeDropdown]/[EditableComboBox]. Same discard-on-back contract as the
 * slot editor: nothing is written until an explicit Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterEditorScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RosterEditorViewModel = hiltViewModel(),
) {
    val existingMember by viewModel.existingMember.collectAsState()
    val showMoves by viewModel.showMoves.collectAsState()
    var draft by remember(existingMember) { mutableStateOf(existingMember?.let { RosterDraft.from(it) } ?: RosterDraft.blank()) }
    var abilityQuery by remember(draft.id) { mutableStateOf(draft.ability.orEmpty()) }
    val abilityResults by remember(abilityQuery) { viewModel.searchAbilities(abilityQuery) }.collectAsState(initial = emptyList())

    BackHandler(onBack = onBackClick)

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (viewModel.customId == null) R.string.roster_new_title else R.string.roster_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (viewModel.customId != null) {
                        IconButton(onClick = { viewModel.delete(onDeleted = onBackClick) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.roster_action_delete))
                        }
                    }
                    IconButton(
                        onClick = { viewModel.save(draft.toTeamMember(), onSaved = onBackClick) },
                        enabled = draft.speciesName.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_confirm))
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
                value = draft.speciesName,
                onValueChange = { draft = draft.copy(speciesName = it) },
                label = { Text(stringResource(R.string.common_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TypeDropdown(
                    label = stringResource(R.string.slot_type1_label),
                    value = draft.type1,
                    nullable = false,
                    onSelect = { type -> draft = draft.copy(type1 = type ?: draft.type1) },
                    modifier = Modifier.weight(1f),
                )
                TypeDropdown(
                    label = stringResource(R.string.slot_type2_label),
                    value = draft.type2,
                    nullable = true,
                    onSelect = { type -> draft = draft.copy(type2 = type) },
                    modifier = Modifier.weight(1f),
                )
            }

            EditableComboBox(
                value = abilityQuery,
                onValueChange = { value ->
                    abilityQuery = value
                    draft = draft.copy(ability = value.ifBlank { null })
                },
                label = stringResource(R.string.slot_ability_label),
                suggestions = abilityResults.map { it.displayName },
                modifier = Modifier.fillMaxWidth(),
            )

            if (showMoves) {
                HorizontalDivider()
                Text(stringResource(R.string.slot_moves_title), style = MaterialTheme.typography.titleSmall)
                for (index in 0 until MOVE_COUNT) {
                    MoveSlotEditor(
                        move = draft.moves.getOrNull(index),
                        searchMoves = viewModel::searchMoves,
                        onChange = { move ->
                            val moves = draft.moves.toMutableList()
                            while (moves.size < MOVE_COUNT) moves.add(null)
                            moves[index] = move
                            draft = draft.copy(moves = moves)
                        },
                    )
                }
            }
        }
    }
}
