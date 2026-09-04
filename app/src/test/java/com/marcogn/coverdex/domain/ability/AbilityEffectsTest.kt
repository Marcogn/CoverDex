package com.marcogn.coverdex.domain.ability

import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityEffectsTest {

    @Test
    fun `normalizeAbilityName lowercases and hyphenates spaces`() {
        assertEquals("flash-fire", normalizeAbilityName("Flash Fire"))
        assertEquals("wonder-guard", normalizeAbilityName("wonder   guard"))
    }

    @Test
    fun `getAbilityEffects returns null for a null, empty, or unrecognized ability`() {
        assertNull(getAbilityEffects(null))
        assertNull(getAbilityEffects(""))
        assertNull(getAbilityEffects("some-random-ability"))
    }

    @Test
    fun `getAbilityEffects is case- and space-insensitive`() {
        assertEquals(getAbilityEffects("flash-fire"), getAbilityEffects("Flash Fire"))
    }

    @Test
    fun `every ABILITY_EFFECTS entry round-trips through getAbilityEffects`() {
        for ((slug, effects) in ABILITY_EFFECTS) {
            assertEquals(effects, getAbilityEffects(slug))
        }
    }

    @Test
    fun `ABILITY_EFFECTS key set matches legacy-web's exactly`() {
        // Ported from abilityEffects.ts: an entry added or removed there must be mirrored here —
        // this assertion fails loudly on a silent drop, per phase-3-analysis.md §1.
        val expectedKeys = setOf(
            "volt-absorb", "lightning-rod", "motor-drive", "water-absorb", "storm-drain",
            "dry-skin", "flash-fire", "sap-sipper", "levitate", "earth-eater",
            "well-baked-body", "thick-fat", "fluffy", "wonder-guard",
        )
        assertEquals(expectedKeys, ABILITY_EFFECTS.keys)
        assertEquals(14, ABILITY_EFFECTS.size)
    }

    @Test
    fun `KNOWN_ABILITIES_WITH_EFFECTS matches legacy-web's display list exactly`() {
        val expected = listOf(
            "volt absorb", "lightning rod", "motor drive", "water absorb", "storm drain",
            "dry skin", "flash fire", "sap sipper", "levitate", "earth eater",
            "well-baked body", "thick fat", "fluffy", "wonder guard",
        )
        assertEquals(expected, KNOWN_ABILITIES_WITH_EFFECTS)
    }

    @Test
    fun `immunity abilities target the expected type`() {
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)), getAbilityEffects("volt-absorb"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.WATER)), getAbilityEffects("water-absorb"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.FIRE)), getAbilityEffects("flash-fire"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.GRASS)), getAbilityEffects("sap-sipper"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.GROUND)), getAbilityEffects("levitate"))
    }

    @Test
    fun `thick-fat halves both Fire and Ice defensively`() {
        val effects = getAbilityEffects("thick-fat")
        assertEquals(
            listOf(
                AbilityEffect.Multiplier(PokemonType.FIRE, 0.5, AbilityEffectSide.DEFENSIVE),
                AbilityEffect.Multiplier(PokemonType.ICE, 0.5, AbilityEffectSide.DEFENSIVE),
            ),
            effects,
        )
    }

    @Test
    fun `wonder-guard is badge-only`() {
        val effects = getAbilityEffects("wonder-guard")
        assertTrue(effects?.single() is AbilityEffect.BadgeOnly)
    }
}
