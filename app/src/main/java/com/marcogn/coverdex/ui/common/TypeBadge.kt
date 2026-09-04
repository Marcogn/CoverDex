package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.sprite.SpriteUrlResolver

/** Renders a type's Scarlet/Violet icon sprite — matches `legacy-web`'s `TypeBadge.tsx`, which
 * uses the same sprite images rather than coloured pill text. */
@Composable
fun TypeBadge(type: PokemonType, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    AsyncImage(
        model = SpriteUrlResolver.typeBadgeUrl(type),
        // A proper localized display name needs a res-backed `PokemonType.displayName()`
        // extension (CLAUDE.md's "enum labels are not on the enum" convention); that lands with
        // the screen that first shows one to a user (Phase 2's slot editor). The raw apiName is
        // an acceptable accessibility-only stopgap until then.
        contentDescription = type.apiName,
        contentScale = ContentScale.Fit,
        modifier = modifier.height(size),
    )
}
