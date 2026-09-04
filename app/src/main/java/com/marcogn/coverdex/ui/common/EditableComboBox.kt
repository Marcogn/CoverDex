package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The "typing aid over free text" control CoverDex's own ability field needs: `legacy-web`'s
 * `AbilityDropdown.tsx` commits whatever the user types directly (`onChange(val)` on every
 * keystroke) and only *offers* cached suggestions as tappable shortcuts — unlike
 * [SearchableDropdown], which only ever commits a picked option. Ported from Hall of Memories'
 * `ui/common/EditableComboBox.kt` (the same "suggest but never reject or rewrite" contract there,
 * for nature/ability/held-item fields) rather than invented fresh — see
 * `docs/plan/README.md`, "Copy Hall of Memories, don't invent." Filtering is the caller's job;
 * this composable only renders whatever [suggestions] it's given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableComboBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val showMenu = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}
