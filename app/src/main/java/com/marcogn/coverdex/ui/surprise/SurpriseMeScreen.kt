package com.marcogn.coverdex.ui.surprise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.common.DropdownOption
import com.marcogn.coverdex.ui.common.PokemonSprite
import com.marcogn.coverdex.ui.common.SearchableDropdown
import com.marcogn.coverdex.ui.common.TypeBadge
import com.marcogn.coverdex.ui.teams.TeamNameDialog

/**
 * The team generator — one scrollable screen (unlike `SurpriseMeModal.tsx`'s three-step wizard;
 * `phase-4-suggestions-and-generator.md` §4 asks only for "an optional anchor picker, the
 * constraint controls, a Generate button, per-slot regenerate, and Keep", styled like the rest of
 * this app's flat Material 3 screens): lock 0-5 anchor Pokémon, tune the constraint counters,
 * Generate, optionally regenerate individual slots or the whole team, then Keep to create a new
 * team from the result.
 */
@Composable
fun SurpriseMeScreen(
    onMenuClick: () -> Unit,
    onTeamCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurpriseMeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var anchorQuery by remember { mutableStateOf("") }
    val anchorResults by remember(anchorQuery) { viewModel.searchSpecies(anchorQuery) }.collectAsState(initial = emptyList())
    var showKeepDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { CoverDexTopBar(title = stringResource(R.string.surprise_me_title), onMenuClick = onMenuClick) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Anchors.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.surprise_me_seed_description), style = MaterialTheme.typography.bodySmall)
                if (state.lockedMembers.size >= 6) {
                    Text(
                        stringResource(R.string.surprise_me_seed_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SearchableDropdown(
                    query = anchorQuery,
                    onQueryChange = { anchorQuery = it },
                    options = anchorResults.map { entry ->
                        DropdownOption(key = "p-${entry.id}", label = entry.displayName, value = entry, pokedexId = entry.id)
                    },
                    selectedLabel = null,
                    onSelect = { option ->
                        option?.let { viewModel.addLocked(it.value) }
                        anchorQuery = ""
                    },
                    placeholder = stringResource(R.string.slot_species_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.lockedMembers.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.lockedMembers.forEachIndexed { index, member ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.removeLocked(index) },
                                label = { Text(member.speciesName) },
                                trailingIcon = { RemoveAnchorIcon() },
                            )
                        }
                    }
                }
            }

            // Constraints.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.surprise_me_remaining_slots, state.remainingSlots),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConstraintCounter(
                    label = stringResource(R.string.surprise_me_starters),
                    value = state.constraints.starterSlots,
                    canIncrement = !state.budgetFull,
                    onIncrement = { viewModel.updateConstraints { it.copy(starterSlots = it.starterSlots + 1) } },
                    onDecrement = { viewModel.updateConstraints { it.copy(starterSlots = (it.starterSlots - 1).coerceAtLeast(0)) } },
                )
                ConstraintCounter(
                    label = stringResource(R.string.surprise_me_legendaries_mythicals),
                    value = state.constraints.legendaryMythicalSlots,
                    canIncrement = !state.budgetFull,
                    onIncrement = { viewModel.updateConstraints { it.copy(legendaryMythicalSlots = it.legendaryMythicalSlots + 1) } },
                    onDecrement = { viewModel.updateConstraints { it.copy(legendaryMythicalSlots = (it.legendaryMythicalSlots - 1).coerceAtLeast(0)) } },
                )
                ConstraintCounter(
                    label = stringResource(R.string.surprise_me_mega_evolutions),
                    value = state.constraints.megaSlots,
                    canIncrement = !state.budgetFull,
                    onIncrement = { viewModel.updateConstraints { it.copy(megaSlots = it.megaSlots + 1) } },
                    onDecrement = { viewModel.updateConstraints { it.copy(megaSlots = (it.megaSlots - 1).coerceAtLeast(0)) } },
                )
                ConstraintCounter(
                    label = stringResource(R.string.surprise_me_dynamax_gmax),
                    value = state.constraints.dynamaxSlots,
                    canIncrement = !state.budgetFull,
                    onIncrement = { viewModel.updateConstraints { it.copy(dynamaxSlots = it.dynamaxSlots + 1) } },
                    onDecrement = { viewModel.updateConstraints { it.copy(dynamaxSlots = (it.dynamaxSlots - 1).coerceAtLeast(0)) } },
                )
                if (state.customs.isNotEmpty()) {
                    ConstraintCounter(
                        label = stringResource(R.string.surprise_me_custom_pokemon),
                        value = state.constraints.customSlots,
                        canIncrement = !state.budgetFull,
                        onIncrement = { viewModel.updateConstraints { it.copy(customSlots = it.customSlots + 1) } },
                        onDecrement = { viewModel.updateConstraints { it.copy(customSlots = (it.customSlots - 1).coerceAtLeast(0)) } },
                    )
                }
            }

            Button(onClick = { viewModel.generate() }, enabled = state.canGenerate, modifier = Modifier.fillMaxWidth()) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.surprise_me_generate))
                }
            }

            // Result.
            if (state.result.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.surprise_me_result_title), style = MaterialTheme.typography.titleMedium)
                    state.warning?.let {
                        Text(
                            stringResource(R.string.surprise_me_too_few_pokemon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height((((state.result.size + 2) / 3) * 140).dp),
                    ) {
                        items(state.result.size) { index ->
                            val member = state.result[index]
                            val isLocked = index < state.lockedMembers.size
                            ResultSlotCard(
                                speciesName = member.speciesName,
                                pokedexId = member.pokedexId,
                                types = member.types,
                                ability = member.ability,
                                canRegenerate = !isLocked && !state.isGenerating,
                                onRegenerate = { viewModel.regenerateSlot(index) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.regenerateAll() },
                            enabled = !state.isGenerating,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.surprise_me_regenerate_all))
                        }
                        Button(
                            onClick = { showKeepDialog = true },
                            enabled = !state.isGenerating,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.surprise_me_keep))
                        }
                    }
                }
            }
        }
    }

    if (showKeepDialog) {
        TeamNameDialog(
            title = stringResource(R.string.teams_new_team_title),
            initialName = "",
            onConfirm = { name ->
                showKeepDialog = false
                viewModel.keep(name, onCreated = onTeamCreated)
            },
            onDismiss = { showKeepDialog = false },
        )
    }
}

@Composable
private fun RemoveAnchorIcon() {
    androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
}

@Composable
private fun ConstraintCounter(
    label: String,
    value: Int,
    canIncrement: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, enabled = value > 0) { Text("−") }
        Text(value.toString(), modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onIncrement, enabled = canIncrement) { Text("+") }
    }
}

@Composable
private fun ResultSlotCard(
    speciesName: String,
    pokedexId: Int?,
    types: Pair<com.marcogn.coverdex.domain.model.PokemonType, com.marcogn.coverdex.domain.model.PokemonType?>,
    ability: String?,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PokemonSprite(pokemonId = pokedexId, context = SpriteContext.CARD, modifier = Modifier.size(48.dp))
            Text(speciesName, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TypeBadge(types.first, size = 14.dp)
                types.second?.let { TypeBadge(it, size = 14.dp) }
            }
            ability?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            if (canRegenerate) {
                IconButton(onClick = onRegenerate, modifier = Modifier.size(28.dp)) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Casino,
                        contentDescription = stringResource(R.string.surprise_me_regenerate_slot),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
