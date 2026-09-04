package com.marcogn.coverdex.ui.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.ui.common.PokemonSprite
import com.marcogn.coverdex.ui.common.TypeBadge

/** One roster entry — `legacy-web`'s `CustomPkmnPage` list row, minus the inline-editable name
 * (this app opens the full editor instead, per `phase-2-teams-and-roster.md`'s "edit" bullet). */
@Composable
fun RosterEntryCard(
    member: TeamMember,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PokemonSprite(pokemonId = member.pokedexId, context = SpriteContext.CARD, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.speciesName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeBadge(member.types.first)
                    member.types.second?.let { TypeBadge(it) }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.roster_action_delete))
            }
        }
    }
}
