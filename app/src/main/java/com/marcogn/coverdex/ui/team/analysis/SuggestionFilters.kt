package com.marcogn.coverdex.ui.team.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R

/**
 * The generation dropdown and "include custom Pokémon" toggle that feed
 * [com.marcogn.coverdex.domain.suggestion.SuggestionOptions] — `phase-4-suggestions-and-generator.md`
 * §4. "Exclude legendaries" and "include Mega/Dynamax" moved to Settings as of Phase 5
 * (`phase-5-import-export-and-settings.md` §3: both are read-only, app-wide preferences in
 * `legacy-web`'s own `TeamDetailPage.tsx` — neither has an `onChange` prop there — so there is no
 * per-screen override to build here). Deliberately smaller than `legacy-web`'s own
 * `SuggestionFilters.tsx`, which also carries a type-filter chip row and a best/random mode
 * toggle: neither is in this app's UI spec.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionFilters(
    generation: Int?,
    onGenerationChange: (Int?) -> Unit,
    includeCustoms: Boolean,
    onIncludeCustomsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val allGenerationsLabel = stringResource(R.string.suggestions_filter_generation_all)
    val generationLabel = generation?.let { stringResource(R.string.suggestions_filter_generation, it) } ?: allGenerationsLabel

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = generationLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(allGenerationsLabel) },
                    onClick = { onGenerationChange(null); expanded = false },
                )
                (1..9).forEach { gen ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.suggestions_filter_generation, gen)) },
                        onClick = { onGenerationChange(gen); expanded = false },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.suggestions_include_customs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = includeCustoms, onCheckedChange = onIncludeCustomsChange)
        }
    }
}
