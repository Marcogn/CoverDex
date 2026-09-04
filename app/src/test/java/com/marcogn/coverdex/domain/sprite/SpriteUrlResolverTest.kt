package com.marcogn.coverdex.domain.sprite

import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Test

/** URL strings are asserted verbatim against docs/plan/reference-pokedata.md §5, which verified
 * every one of them against the live sprite mirror. */
class SpriteUrlResolverTest {

    @Test
    fun `card context returns HOME then official-artwork then the flat pixel sprite`() {
        val candidates = SpriteUrlResolver.resolveSpriteCandidates(6, SpriteContext.CARD)

        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/6.png",
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/6.png",
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/6.png",
            ),
            candidates,
        )
    }

    @Test
    fun `dropdown context returns only the flat pixel sprite`() {
        val candidates = SpriteUrlResolver.resolveSpriteCandidates(6, SpriteContext.DROPDOWN)

        assertEquals(
            listOf("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/6.png"),
            candidates,
        )
    }

    @Test
    fun `works for an alternate form id above 10000`() {
        val candidates = SpriteUrlResolver.resolveSpriteCandidates(10034, SpriteContext.CARD)

        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/10034.png",
            candidates.first(),
        )
    }

    @Test
    fun `type badge url matches legacy-web's getTypeSpriteUrl for every type`() {
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/types/generation-ix/scarlet-violet/small/1.png",
            SpriteUrlResolver.typeBadgeUrl(PokemonType.NORMAL),
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/types/generation-ix/scarlet-violet/small/18.png",
            SpriteUrlResolver.typeBadgeUrl(PokemonType.FAIRY),
        )
    }

    @Test
    fun `every PokemonType id matches legacy-web's TYPE_SPRITE_IDS mapping`() {
        // legacy-web/src/data/typeSprites.ts TYPE_SPRITE_IDS, transcribed for comparison.
        val expected = mapOf(
            "normal" to 1, "fighting" to 2, "flying" to 3, "poison" to 4, "ground" to 5,
            "rock" to 6, "bug" to 7, "ghost" to 8, "steel" to 9, "fire" to 10, "water" to 11,
            "grass" to 12, "electric" to 13, "psychic" to 14, "ice" to 15, "dragon" to 16,
            "dark" to 17, "fairy" to 18,
        )
        for (type in PokemonType.entries) {
            assertEquals("id mismatch for ${type.apiName}", expected.getValue(type.apiName), type.id)
        }
    }
}
