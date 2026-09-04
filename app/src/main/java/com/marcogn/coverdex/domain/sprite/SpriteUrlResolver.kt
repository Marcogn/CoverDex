package com.marcogn.coverdex.domain.sprite

import com.marcogn.coverdex.domain.model.PokemonType

/** Where a sprite is being shown — the two contexts `legacy-web`'s `resolveSpriteUrl` (spec's
 * source of truth for this) distinguishes. */
enum class SpriteContext { CARD, DROPDOWN }

/**
 * Pure sprite URL derivation — no sprite URL is ever stored in the database (see
 * docs/plan/native-spec.md, "Dataset"). Verified against the live mirror in
 * docs/plan/reference-pokedata.md §5.
 */
object SpriteUrlResolver {

    private const val SPRITES_BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"
    private const val TYPE_SPRITES_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/types/generation-ix/scarlet-violet/small"

    /**
     * Ordered candidates, most specific first — the UI ([com.marcogn.coverdex.ui.common.PokemonSprite])
     * tries them in order and advances on load failure, since Coil has no built-in fallback chain.
     *
     * CARD (team slots, suggestion cards): HOME render -> official artwork -> pixel sprite.
     * DROPDOWN (list thumbnails): pixel sprite only.
     */
    fun resolveSpriteCandidates(pokemonId: Int, context: SpriteContext): List<String> =
        when (context) {
            SpriteContext.CARD -> listOf(
                "$SPRITES_BASE/other/home/$pokemonId.png",
                "$SPRITES_BASE/other/official-artwork/$pokemonId.png",
                "$SPRITES_BASE/$pokemonId.png",
            )
            SpriteContext.DROPDOWN -> listOf("$SPRITES_BASE/$pokemonId.png")
        }

    /** Matches `legacy-web/src/data/typeSprites.ts`'s `getTypeSpriteUrl` — same ids as
     * [PokemonType.id], the Scarlet/Violet small type-icon set. */
    fun typeBadgeUrl(type: PokemonType): String = "$TYPE_SPRITES_BASE/${type.id}.png"
}
