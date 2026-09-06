package com.marcogn.coverdex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.item.ITEM_EFFECTS
import com.marcogn.coverdex.domain.pokeapi.prettify

/**
 * The item field — free text with suggestions from the modelled defensive subset
 * ([ITEM_EFFECTS]), the same "suggest but never reject" contract as
 * [AbilityPicker]'s free-text mode: there is no cached item catalogue (`items.csv` is
 * deliberately not downloaded, see docs/plan/phase-7-accuracy-and-customization.md §4), so any
 * item name is accepted, modelled or not. [resetKey] is the caller's draft identity, same reason
 * as [AbilityPicker]'s.
 */
@Composable
fun ItemPicker(
    resetKey: Any,
    item: String?,
    onItemChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember(resetKey) { mutableStateOf(item.orEmpty()) }
    val suggestions = remember(query) {
        val key = query.trim().lowercase()
        MODELLED_ITEM_DISPLAY_NAMES.filter { key.isEmpty() || it.lowercase().contains(key) }
    }

    EditableComboBox(
        value = query,
        onValueChange = { value ->
            query = value
            onItemChange(value.ifBlank { null })
        },
        label = stringResource(R.string.slot_item_label),
        suggestions = suggestions,
        modifier = modifier,
    )
}

private val MODELLED_ITEM_DISPLAY_NAMES: List<String> = ITEM_EFFECTS.keys.map { prettify(it) }.sorted()
