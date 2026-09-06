package com.marcogn.coverdex.ui.team.analysis

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.ability.AbilityEffect
import com.marcogn.coverdex.domain.ability.getAbilityEffects
import com.marcogn.coverdex.domain.coverage.attackingTypesForMember
import com.marcogn.coverdex.domain.coverage.defensiveMultiplier
import com.marcogn.coverdex.domain.coverage.memberHasMoves
import com.marcogn.coverdex.domain.item.ItemEffect
import com.marcogn.coverdex.domain.item.getItemEffects
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.ui.common.PokemonSprite
import com.marcogn.coverdex.ui.common.TypeBadge
import com.marcogn.coverdex.ui.common.displayName

/** Section B, one row: sprite, types, and — expanded — its weaknesses/resistances/immunities
 * bucketed by multiplier, its ability's coverage effect, and its move-type coverage. Collapsed
 * by default, per `analysisPage.integration.test.tsx`. */
@Composable
fun PerPokemonCard(member: TeamMember, chart: TypeChart, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PokemonSprite(pokemonId = member.pokedexId, context = SpriteContext.DROPDOWN, modifier = Modifier.size(32.dp))
            Text(member.speciesName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TypeBadge(member.types.first)
            member.types.second?.let { TypeBadge(it) }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Same per-type bucketing as CoverageGrid.tsx's PerPokemonCard — one
                // defensiveMultiplier call per type, not a post-hoc split of defensiveProfile's
                // coarser weakness/resistance buckets (which would lose the ability's effect on
                // the exact multiplier and the 2x/4x, 0.5x/0.25x distinction).
                val weak4x = mutableListOf<PokemonType>()
                val weak2x = mutableListOf<PokemonType>()
                val resist05x = mutableListOf<PokemonType>()
                val resist025x = mutableListOf<PokemonType>()
                val immune = mutableListOf<PokemonType>()
                for (atk in PokemonType.entries) {
                    when (val mult = defensiveMultiplier(chart, atk, member.types, member.ability, member.item)) {
                        0.0 -> immune.add(atk)
                        else -> when {
                            mult >= 4.0 -> weak4x.add(atk)
                            mult >= 2.0 -> weak2x.add(atk)
                            mult <= 0.25 -> resist025x.add(atk)
                            mult < 1.0 -> resist05x.add(atk)
                        }
                    }
                }

                if (!member.ability.isNullOrEmpty()) {
                    // effect.type.displayName() and the "immune to" label are both @Composable
                    // (stringResource-backed) — resolved here, in the composable context, rather
                    // than inside the plain joinToString lambda below, which cannot call them.
                    val immuneToLabel = stringResource(R.string.analysis_immune_to)
                    val superEffectiveHitsLabel = stringResource(R.string.analysis_super_effective_hits)
                    val neverSuperEffectiveLabel = stringResource(R.string.analysis_never_super_effective)
                    val typeNames = PokemonType.entries.associateWith { it.displayName() }
                    val effects = getAbilityEffects(member.ability)
                    val isWonderGuard = effects?.any { it is AbilityEffect.OnlySuperEffective } == true
                    val summary = effects
                        ?.filter { it !is AbilityEffect.BadgeOnly && it !is AbilityEffect.OnlySuperEffective }
                        ?.joinToString(", ") { effect ->
                            when (effect) {
                                is AbilityEffect.Immunity -> "$immuneToLabel ${typeNames.getValue(effect.type)}"
                                is AbilityEffect.Multiplier -> "×${effect.factor} ${typeNames.getValue(effect.type)}"
                                is AbilityEffect.SuperEffectiveMultiplier -> "×${effect.factor} $superEffectiveHitsLabel"
                                AbilityEffect.NeverSuperEffective -> neverSuperEffectiveLabel
                                is AbilityEffect.BadgeOnly, AbilityEffect.OnlySuperEffective -> ""
                            }
                        }
                    DefRow(stringResource(R.string.slot_ability_label)) {
                        Text(member.ability, style = MaterialTheme.typography.bodySmall)
                        if (!summary.isNullOrEmpty()) {
                            Text(" — $summary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (isWonderGuard) {
                            Text(" — ${stringResource(R.string.analysis_wonder_guard_note)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                if (!member.item.isNullOrEmpty()) {
                    val immuneToLabel = stringResource(R.string.analysis_immune_to)
                    val groundsHolderLabel = stringResource(R.string.analysis_item_grounds_holder)
                    val removesImmunitiesLabel = stringResource(R.string.analysis_item_removes_immunities)
                    val typeNames = PokemonType.entries.associateWith { it.displayName() }
                    val itemSummary = getItemEffects(member.item)?.joinToString(", ") { effect ->
                        when (effect) {
                            is ItemEffect.Immunity -> "$immuneToLabel ${typeNames.getValue(effect.type)}"
                            ItemEffect.GroundsHolder -> groundsHolderLabel
                            ItemEffect.RemovesTypeImmunities -> removesImmunitiesLabel
                            is ItemEffect.ResistBerry -> "×0.5 ${typeNames.getValue(effect.type)}"
                        }
                    }
                    DefRow(stringResource(R.string.slot_item_label)) {
                        Text(member.item, style = MaterialTheme.typography.bodySmall)
                        if (!itemSummary.isNullOrEmpty()) {
                            Text(" — $itemSummary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                if (weak4x.isNotEmpty()) TypeRow(stringResource(R.string.defensive_weaknesses_4x), weak4x)
                if (weak2x.isNotEmpty()) TypeRow(stringResource(R.string.defensive_weaknesses_2x), weak2x)
                if (resist05x.isNotEmpty()) TypeRow(stringResource(R.string.defensive_resistances_05x), resist05x)
                if (resist025x.isNotEmpty()) TypeRow(stringResource(R.string.defensive_resistances_025x), resist025x)
                if (immune.isNotEmpty()) TypeRow(stringResource(R.string.defensive_immune_0x), immune)

                if (memberHasMoves(member)) {
                    TypeRow(stringResource(R.string.defensive_move_coverage), attackingTypesForMember(member))
                }
            }
        }
    }
}

@Composable
private fun DefRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Row(content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeRow(label: String, types: List<PokemonType>) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            types.forEach { TypeBadge(it) }
        }
    }
}
