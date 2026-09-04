package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.sprite.SpriteContext

/**
 * One row's data — `legacy-web`'s `DropdownOption<T>` (`SearchableDropdown.tsx`). [pokedexId]
 * drives the row's sprite thumbnail (resolved here, via [SpriteContext.DROPDOWN] — never a stored
 * URL); `null` for a move/ability row, or a custom Pokémon with no cached id.
 */
data class DropdownOption<T>(
    val key: String,
    val label: String,
    val value: T,
    val pokedexId: Int? = null,
    val group: String? = null,
)

/**
 * The shared searchable picker every species/move/ability/generator-anchor field uses — the
 * contract from `docs/plan/native-spec.md`, "Searchable dropdowns": no items on focus with an
 * empty query, all matches (no cap) from the first typed character, the list scrolls internally.
 *
 * Fully controlled: [query]/[onQueryChange] and [options] (already filtered for the current
 * [query] — a Room search, not a client-side filter, since the catalogue is not held in memory)
 * are owned by the caller. On its own, this composable only owns whether it is expanded, which it
 * derives from focus plus content: focused with a blank query and nothing selected shows nothing
 * (matching the spec), but focused with a blank query *and* a current selection still shows a
 * "Clear selection" row — `legacy-web`'s own dropdown always offers that regardless of query,
 * and hiding it here just because nothing has been typed yet would be a real loss of the feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<DropdownOption<T>>,
    selectedLabel: String?,
    onSelect: (DropdownOption<T>?) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val expanded = hasFocus && (query.isNotBlank() || selectedLabel != null)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {}, modifier = modifier) {
        OutlinedTextField(
            value = if (hasFocus) query else (selectedLabel ?: ""),
            onValueChange = onQueryChange,
            placeholder = { Text(if (hasFocus) stringResource(R.string.common_search_placeholder) else placeholder) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                .onFocusChanged { state ->
                    if (state.isFocused && !hasFocus) onQueryChange("")
                    hasFocus = state.isFocused
                },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { hasFocus = false }) {
            if (selectedLabel != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_clear_selection)) },
                    onClick = {
                        onSelect(null)
                        hasFocus = false
                    },
                )
            }
            if (query.isNotBlank()) {
                if (options.isEmpty()) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.common_no_matches)) }, onClick = {}, enabled = false)
                } else {
                    options.forEach { option ->
                        key(option.key) {
                            DropdownMenuItem(
                                text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = option.pokedexId?.let { id ->
                                    { PokemonSprite(pokemonId = id, context = SpriteContext.DROPDOWN, modifier = Modifier.size(24.dp)) }
                                },
                                trailingIcon = option.group?.let { group ->
                                    { Text(group, style = MaterialTheme.typography.labelSmall) }
                                },
                                onClick = {
                                    onSelect(option)
                                    hasFocus = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
