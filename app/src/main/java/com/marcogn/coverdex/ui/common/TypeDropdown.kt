package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.PokemonType

/** A plain pick-one-of-18 dropdown for a type override — `legacy-web`'s `<select>` over
 * [PokemonType], not a [SearchableDropdown] (a fixed 18-item list needs no search). [nullable]
 * adds a "None" entry first, for a slot's optional second type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeDropdown(
    label: String,
    value: PokemonType?,
    nullable: Boolean,
    onSelect: (PokemonType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value?.displayName() ?: stringResource(R.string.slot_type_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (nullable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.slot_type_none)) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
            }
            PokemonType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName()) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
