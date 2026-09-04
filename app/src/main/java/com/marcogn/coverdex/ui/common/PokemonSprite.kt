package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.marcogn.coverdex.domain.sprite.SpriteContext
import com.marcogn.coverdex.domain.sprite.SpriteUrlResolver

/**
 * Renders a Pokémon sprite for [pokemonId], walking [SpriteUrlResolver.resolveSpriteCandidates]
 * on load failure — Coil has no built-in fallback chain, so this composable is it. Falls back to
 * a placeholder icon once every candidate has failed to load. [pokemonId] is `null` for a custom
 * Pokémon (no cached id), which always renders the placeholder.
 */
@Composable
fun PokemonSprite(
    pokemonId: Int?,
    context: SpriteContext,
    modifier: Modifier = Modifier,
) {
    val candidates = remember(pokemonId, context) {
        pokemonId?.let { SpriteUrlResolver.resolveSpriteCandidates(it, context) } ?: emptyList()
    }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val localContext = LocalContext.current

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (index < candidates.size) {
            AsyncImage(
                model = remember(candidates[index]) {
                    ImageRequest.Builder(localContext).data(candidates[index]).crossfade(true).build()
                },
                contentDescription = null,
                onError = { index++ },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.CatchingPokemon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}
