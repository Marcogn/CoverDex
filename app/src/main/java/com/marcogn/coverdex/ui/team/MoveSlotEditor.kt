package com.marcogn.coverdex.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
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
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.ui.common.DamageClassDropdown
import com.marcogn.coverdex.ui.common.DropdownOption
import com.marcogn.coverdex.ui.common.SearchableDropdown
import com.marcogn.coverdex.ui.common.TypeDropdown
import com.marcogn.coverdex.ui.common.displayName
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * One of a slot's four move fields — `legacy-web`'s `MoveSlot.tsx`, ported behaviourally rather
 * than from `phase-2-teams-and-roster.md`'s own paraphrase (see
 * `docs/implementation-decisions.md`, "Phase 2"): a cached move is picked from
 * [SearchableDropdown]; a name typed into the plain text field below that has no cache match
 * becomes `isCustom = true` with `type = NORMAL`, `damageClass = PHYSICAL`, `power = null` —
 * `MoveSlot.tsx`'s own defaults for a brand-new custom move — and only then do the type/power/
 * damage-class fields appear so the user can complete it.
 */
@Composable
fun MoveSlotEditor(
    move: PokemonMove?,
    searchMoves: (String) -> Flow<List<MoveEntry>>,
    onChange: (PokemonMove?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results by remember(query) { searchMoves(query) }.collectAsState(initial = emptyList())

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SearchableDropdown(
            query = query,
            onQueryChange = { query = it },
            options = results.map { entry ->
                val powerSuffix = entry.power?.let { " · $it" } ?: ""
                DropdownOption(
                    key = "m-${entry.id}",
                    label = "${entry.displayName} · ${entry.type.displayName()}$powerSuffix",
                    value = entry,
                )
            },
            selectedLabel = move?.takeIf { !it.isCustom }?.name,
            onSelect = { option ->
                if (option == null) {
                    onChange(null)
                } else {
                    val entry = option.value
                    onChange(
                        PokemonMove(
                            id = UUID.randomUUID().toString(),
                            name = entry.displayName,
                            type = entry.type,
                            power = entry.power,
                            damageClass = entry.damageClass,
                            isCustom = false,
                        ),
                    )
                }
            },
            placeholder = stringResource(R.string.slot_move_pick_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = move?.takeIf { it.isCustom }?.name.orEmpty(),
            onValueChange = { name ->
                if (name.isBlank()) {
                    onChange(null)
                } else {
                    onChange(
                        PokemonMove(
                            id = move?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            type = move?.type ?: PokemonType.NORMAL,
                            power = move?.power,
                            damageClass = move?.damageClass ?: DamageClass.PHYSICAL,
                            isCustom = true,
                        ),
                    )
                }
            },
            placeholder = { Text(stringResource(R.string.slot_move_custom_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (move != null && move.isCustom) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TypeDropdown(
                    label = stringResource(R.string.slot_move_type_label),
                    value = move.type,
                    nullable = false,
                    onSelect = { type -> onChange(move.copy(type = type ?: move.type)) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = move.power?.toString().orEmpty(),
                    onValueChange = { text -> onChange(move.copy(power = text.toIntOrNull())) },
                    label = { Text(stringResource(R.string.slot_move_power_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            DamageClassDropdown(
                label = stringResource(R.string.slot_move_damage_class_label),
                value = move.damageClass,
                onSelect = { damageClass -> onChange(move.copy(damageClass = damageClass)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
