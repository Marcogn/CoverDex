package com.marcogn.coverdex.ui.team.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.domain.suggestion.Suggestion
import com.marcogn.coverdex.ui.common.PokemonSprite
import com.marcogn.coverdex.ui.common.TypeBadge

/**
 * One ranked candidate — sprite, name, type badges, gain, composite score, newly covered types
 * and new weaknesses, plus (in replacement mode) which member it would replace. Tapping the card
 * applies it — `phase-4-suggestions-and-generator.md` §4: "A tap adds or swaps it into the team."
 */
@Composable
fun SuggestionCard(suggestion: Suggestion, onApply: (Suggestion) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { onApply(suggestion) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PokemonSprite(
                pokemonId = suggestion.candidate.pokedexId,
                context = SpriteContext.CARD,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(suggestion.candidateLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeBadge(suggestion.types.first)
                    suggestion.types.second?.let { TypeBadge(it) }
                }
            }
        }

        Text(
            if (suggestion.kind == Suggestion.Kind.ADD) {
                stringResource(R.string.suggestions_add_to_team)
            } else {
                stringResource(R.string.suggestions_replaces, suggestion.replacesName.orEmpty())
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            stringResource(R.string.suggestions_score, "%.1f".format(suggestion.compositeScore)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (suggestion.newlyCovered.isNotEmpty()) {
            TypeRow(label = stringResource(R.string.suggestions_covers), types = suggestion.newlyCovered)
        }
        if (suggestion.newWeaknesses.isNotEmpty()) {
            TypeRow(label = stringResource(R.string.suggestions_new_weaknesses), types = suggestion.newWeaknesses)
        }
        if (suggestion.aggravatedWeaknesses.isNotEmpty()) {
            TypeRow(label = stringResource(R.string.suggestions_aggravates), types = suggestion.aggravatedWeaknesses)
        }
        if (suggestion.newWeaknesses.isEmpty() && suggestion.aggravatedWeaknesses.isEmpty()) {
            Text(
                stringResource(R.string.suggestions_no_new_weaknesses),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun TypeRow(label: String, types: List<com.marcogn.coverdex.domain.model.PokemonType>) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        types.forEach { TypeBadge(it, size = 16.dp) }
    }
}
