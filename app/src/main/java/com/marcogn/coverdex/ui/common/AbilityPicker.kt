package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.ability.ABILITY_EFFECTS
import com.marcogn.coverdex.domain.ability.abilityKey
import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.SpeciesAbility
import kotlinx.coroutines.flow.Flow

/**
 * The ability field's canonical-plus-custom picker — `docs/plan/phase-7-accuracy-and-customization.md`
 * §3.2. A species picked from the cache ([pokedexId] non-null) starts in its canonical list
 * (normal abilities, then hidden, then a final "Custom ability…" row); a hand-typed or roster
 * Pokémon ([pokedexId] null) has no canonical list and goes straight to the free-text picker,
 * matching [EditableComboBox]'s existing "suggest but never reject" contract — a ROM hack's
 * ability that exists in no PokéAPI table must still be typeable. A canonical option that has a
 * coverage effect ([ABILITY_EFFECTS]) is marked with a small dot, per the plan's "don't filter
 * the non-affecting ones out, Moxie and Intimidate stay selectable". [resetKey] is the caller's
 * draft identity (a fresh id on every new species pick, e.g. `SlotDraft.id`) — every bit of local
 * state here is keyed on it, not on [pokedexId] alone, since two different hand-typed/roster
 * drafts both have `pokedexId == null` and must never share remembered state.
 */
@Composable
fun AbilityPicker(
    resetKey: Any,
    pokedexId: Int?,
    ability: String?,
    onAbilityChange: (String?) -> Unit,
    searchAbilities: (String) -> Flow<List<AbilityEntry>>,
    loadCanonicalAbilities: suspend (Int) -> List<SpeciesAbility>,
    modifier: Modifier = Modifier,
) {
    var canonical by remember(resetKey) { mutableStateOf<List<SpeciesAbility>>(emptyList()) }
    LaunchedEffect(resetKey) {
        canonical = pokedexId?.let { loadCanonicalAbilities(it) }.orEmpty()
    }
    // Starts on the canonical list whenever one exists for the current species; a hand-typed or
    // roster Pokemon (no pokedexId, so canonical is always empty) goes straight to free text.
    // Keyed on resetKey (the draft's own identity, fresh on every species pick), not pokedexId
    // alone — two different custom/hand-typed drafts both have pokedexId == null and must not
    // share remembered state.
    var customMode by remember(resetKey) { mutableStateOf(false) }

    if (canonical.isNotEmpty() && !customMode) {
        CanonicalAbilityDropdown(
            abilities = canonical,
            selected = ability,
            onSelect = onAbilityChange,
            onCustomRequested = { customMode = true },
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FreeTextAbilityField(resetKey = resetKey, ability = ability, onAbilityChange = onAbilityChange, searchAbilities = searchAbilities)
            if (canonical.isNotEmpty()) {
                TextButton(onClick = { customMode = false }) {
                    Text(stringResource(R.string.ability_picker_back_to_canonical))
                }
            }
        }
    }
}

private fun hasEffect(nameOrSlug: String): Boolean = abilityKey(nameOrSlug) in ABILITY_EFFECTS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanonicalAbilityDropdown(
    abilities: List<SpeciesAbility>,
    selected: String?,
    onSelect: (String) -> Unit,
    onCustomRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val hiddenSuffix = stringResource(R.string.ability_picker_hidden_suffix)
    val customLabel = stringResource(R.string.ability_picker_custom_option)
    val effectMarker = stringResource(R.string.ability_picker_effect_marker)

    fun labelFor(a: SpeciesAbility) = a.displayName + if (a.isHidden) hiddenSuffix else ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.slot_ability_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            abilities.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(labelFor(a))
                            if (hasEffect(a.slug)) Text(effectMarker)
                        }
                    },
                    onClick = {
                        onSelect(a.displayName)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(customLabel) },
                onClick = {
                    expanded = false
                    onCustomRequested()
                },
            )
        }
    }
}

@Composable
private fun FreeTextAbilityField(
    resetKey: Any,
    ability: String?,
    onAbilityChange: (String?) -> Unit,
    searchAbilities: (String) -> Flow<List<AbilityEntry>>,
) {
    var query by remember(resetKey) { mutableStateOf(ability.orEmpty()) }
    val results by remember(query) { searchAbilities(query) }.collectAsState(initial = emptyList())

    EditableComboBox(
        value = query,
        onValueChange = { value ->
            query = value
            onAbilityChange(value.ifBlank { null })
        },
        label = stringResource(R.string.slot_ability_label),
        suggestions = results.map { it.displayName },
        modifier = Modifier.fillMaxWidth(),
    )
}
