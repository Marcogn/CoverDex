package com.marcogn.coverdex.ui.team

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.ui.common.DropdownOption
import com.marcogn.coverdex.ui.common.EditableComboBox
import com.marcogn.coverdex.ui.common.PokemonSprite
import com.marcogn.coverdex.ui.common.SearchableDropdown
import com.marcogn.coverdex.ui.common.TypeBadge
import com.marcogn.coverdex.ui.common.TypeDropdown
import java.util.UUID

private const val MOVE_COUNT = 4

private data class SlotDraft(
    val id: String,
    val pokedexId: Int?,
    val speciesName: String,
    val type1: PokemonType,
    val type2: PokemonType?,
    val ability: String?,
    val moves: List<PokemonMove?>,
    val isCustomSaved: Boolean,
) {
    fun toTeamMember(): TeamMember = TeamMember(
        id = id,
        pokedexId = pokedexId,
        speciesName = speciesName,
        types = type1 to type2,
        ability = ability,
        moves = moves,
        isCustomSaved = isCustomSaved,
    )

    companion object {
        fun from(member: TeamMember): SlotDraft = SlotDraft(
            id = member.id,
            pokedexId = member.pokedexId,
            speciesName = member.speciesName,
            type1 = member.types.first,
            type2 = member.types.second,
            ability = member.ability,
            moves = member.moves,
            isCustomSaved = member.isCustomSaved,
        )
    }
}

/**
 * The slot editor — `docs/plan/phase-2-teams-and-roster.md` §3's "Team detail — Pokémon tab".
 * Picking a species always replaces the whole draft (fresh id, reset ability/moves), matching
 * `legacy-web`'s `PokemonSlot.selectPokemon`; every other field edits the same draft in place.
 * Nothing is written to Room until [SlotEditorViewModel.save] — the system back gesture (and the
 * top bar's own back icon) both discard the in-progress draft, per CLAUDE.md's "Known gotchas".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditorScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SlotEditorViewModel = hiltViewModel(),
) {
    val existingMember by viewModel.existingMember.collectAsState()
    val showMoves by viewModel.showMoves.collectAsState()
    var draft by remember(existingMember) { mutableStateOf(existingMember?.let { SlotDraft.from(it) }) }
    var speciesQuery by remember { mutableStateOf("") }
    val speciesResults by remember(speciesQuery) { viewModel.searchSpecies(speciesQuery) }.collectAsState(initial = emptyList())

    BackHandler(onBack = onBackClick)

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.slot_editor_title, viewModel.slotIndex + 1)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { draft?.let { viewModel.save(it.toTeamMember(), onSaved = onBackClick) } },
                        enabled = draft != null,
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
            SearchableDropdown(
                query = speciesQuery,
                onQueryChange = { speciesQuery = it },
                options = speciesResults.map { entry ->
                    DropdownOption(key = "p-${entry.id}", label = entry.displayName, value = entry, pokedexId = entry.id)
                },
                selectedLabel = draft?.speciesName,
                onSelect = { option ->
                    draft = option?.let { picked ->
                        val entry = picked.value
                        SlotDraft(
                            id = UUID.randomUUID().toString(),
                            pokedexId = entry.id,
                            speciesName = entry.displayName,
                            type1 = entry.types.first,
                            type2 = entry.types.second,
                            ability = entry.defaultAbility,
                            moves = List(MOVE_COUNT) { null },
                            isCustomSaved = false,
                        )
                    }
                },
                placeholder = stringResource(R.string.slot_species_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )

            val currentDraft = draft
            if (currentDraft == null) {
                Text(
                    stringResource(R.string.slot_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PokemonSprite(pokemonId = currentDraft.pokedexId, context = SpriteContext.CARD, modifier = Modifier.size(64.dp))
                    Column {
                        Text(currentDraft.speciesName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TypeBadge(currentDraft.type1)
                            currentDraft.type2?.let { TypeBadge(it) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TypeDropdown(
                        label = stringResource(R.string.slot_type1_label),
                        value = currentDraft.type1,
                        nullable = false,
                        onSelect = { type -> draft = currentDraft.copy(type1 = type ?: currentDraft.type1) },
                        modifier = Modifier.weight(1f),
                    )
                    TypeDropdown(
                        label = stringResource(R.string.slot_type2_label),
                        value = currentDraft.type2,
                        nullable = true,
                        onSelect = { type -> draft = currentDraft.copy(type2 = type) },
                        modifier = Modifier.weight(1f),
                    )
                }

                var abilityQuery by remember(currentDraft.id) { mutableStateOf(currentDraft.ability.orEmpty()) }
                val abilityResults by remember(abilityQuery) { viewModel.searchAbilities(abilityQuery) }.collectAsState(initial = emptyList())
                EditableComboBox(
                    value = abilityQuery,
                    onValueChange = { value ->
                        abilityQuery = value
                        draft = currentDraft.copy(ability = value.ifBlank { null })
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
                            move = currentDraft.moves.getOrNull(index),
                            searchMoves = viewModel::searchMoves,
                            onChange = { move ->
                                val moves = currentDraft.moves.toMutableList()
                                while (moves.size < MOVE_COUNT) moves.add(null)
                                moves[index] = move
                                draft = currentDraft.copy(moves = moves)
                            },
                        )
                    }
                }

                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveToRoster(currentDraft.toTeamMember().let { it.copy(isCustomSaved = true) })
                            draft = currentDraft.copy(isCustomSaved = true)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.slot_save_to_roster))
                    }
                    TextButton(
                        onClick = { viewModel.clearSlot(onCleared = onBackClick) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.slot_clear))
                    }
                }
            }
        }
    }
}
