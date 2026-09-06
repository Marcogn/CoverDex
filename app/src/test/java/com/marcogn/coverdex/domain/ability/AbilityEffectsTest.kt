package com.marcogn.coverdex.domain.ability

import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityEffectsTest {

    @Test
    fun `abilityKey lowercases and strips every symbol, not just spaces`() {
        assertEquals("flashfire", abilityKey("Flash Fire"))
        assertEquals("wonderguard", abilityKey("wonder   guard"))
        assertEquals("wellbakedbody", abilityKey("Well-Baked Body"))
        assertEquals("wellbakedbody", abilityKey("well-baked-body"))
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
    fun `getAbilityEffects resolves a display name with a hyphen the slug does not have`() {
        // Phase 7's own regression: prettify()/ability_names.csv can carry a hyphen an
        // identifier doesn't (or vice versa) — abilityKey() strips symbols on both sides so
        // either spelling resolves. well-baked-body has one hyphen in the slug and one in the
        // display name ("Well-Baked Body"); this asserts the *display* name resolves too.
        assertEquals(getAbilityEffects("well-baked-body"), getAbilityEffects("Well-Baked Body"))
    }

    @Test
    fun `every ABILITY_EFFECTS entry round-trips through getAbilityEffects`() {
        for ((slug, effects) in ABILITY_EFFECTS) {
            assertEquals(effects, getAbilityEffects(slug))
        }
    }

    @Test
    fun `ABILITY_EFFECTS covers every defensive ability the Phase 7 audit found missing`() {
        // docs/plan/phase-7-accuracy-and-customization.md §0.4: heatproof through tera-shell were
        // entirely unmodelled pre-Phase-7; dry-skin only had its Water immunity, missing the Fire
        // 1.25x; wonder-guard was BadgeOnly instead of a real effect.
        val expectedKeys = setOf(
            "volt-absorb", "lightning-rod", "motor-drive", "water-absorb", "storm-drain",
            "dry-skin", "flash-fire", "sap-sipper", "levitate", "earth-eater",
            "well-baked-body", "thick-fat", "fluffy", "wonder-guard",
            "heatproof", "water-bubble", "purifying-salt", "filter", "solid-rock",
            "prism-armor", "primordial-sea", "desolate-land", "delta-stream", "tera-shell",
        )
        assertEquals(expectedKeys, ABILITY_EFFECTS.keys)
        assertEquals(24, ABILITY_EFFECTS.size)
    }

    @Test
    fun `KNOWN_ABILITIES_WITH_EFFECTS is ABILITY_EFFECTS' keys in display format, never hand-drifted`() {
        assertEquals(ABILITY_EFFECTS.keys.map { it.replace('-', ' ') }.toSet(), KNOWN_ABILITIES_WITH_EFFECTS.toSet())
        assertEquals(ABILITY_EFFECTS.size, KNOWN_ABILITIES_WITH_EFFECTS.size)
    }

    @Test
    fun `immunity abilities target the expected type`() {
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)), getAbilityEffects("volt-absorb"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.WATER)), getAbilityEffects("water-absorb"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.FIRE)), getAbilityEffects("flash-fire"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.GRASS)), getAbilityEffects("sap-sipper"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.GROUND)), getAbilityEffects("levitate"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.FIRE)), getAbilityEffects("primordial-sea"))
        assertEquals(listOf(AbilityEffect.Immunity(PokemonType.WATER)), getAbilityEffects("desolate-land"))
    }

    @Test
    fun `dry-skin absorbs Water and takes extra Fire damage, both halves`() {
        val effects = getAbilityEffects("dry-skin")
        assertEquals(
            listOf(
                AbilityEffect.Immunity(PokemonType.WATER),
                AbilityEffect.Multiplier(PokemonType.FIRE, 1.25, AbilityEffectSide.DEFENSIVE),
            ),
            effects,
        )
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
    fun `wonder-guard is a real effect, not badge-only`() {
        assertEquals(listOf(AbilityEffect.OnlySuperEffective), getAbilityEffects("wonder-guard"))
    }

    @Test
    fun `tera-shell stays badge-only, this engine has no HP concept to gate it on`() {
        val effects = getAbilityEffects("tera-shell")
        assertTrue(effects?.single() is AbilityEffect.BadgeOnly)
    }

    // --- applyAbilityEffects ---

    @Test
    fun `applyAbilityEffects is a no-op for a null or empty effect list`() {
        assertEquals(2.0, applyAbilityEffects(2.0, PokemonType.FIRE, null), 0.0)
        assertEquals(2.0, applyAbilityEffects(2.0, PokemonType.FIRE, emptyList()), 0.0)
    }

    @Test
    fun `applyAbilityEffects immunity zeroes out regardless of the base multiplier`() {
        val effects = listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC))
        assertEquals(0.0, applyAbilityEffects(4.0, PokemonType.ELECTRIC, effects), 0.0)
        assertEquals(0.0, applyAbilityEffects(0.0, PokemonType.ELECTRIC, effects), 0.0)
    }

    @Test
    fun `applyAbilityEffects wonder-guard blocks a non-super-effective hit but lets a super-effective one through`() {
        val effects = listOf(AbilityEffect.OnlySuperEffective)
        assertEquals(0.0, applyAbilityEffects(1.0, PokemonType.FIRE, effects), 0.0)
        assertEquals(0.0, applyAbilityEffects(0.5, PokemonType.FIRE, effects), 0.0)
        assertEquals(2.0, applyAbilityEffects(2.0, PokemonType.DARK, effects), 0.0)
    }

    @Test
    fun `applyAbilityEffects filter-solid-rock-prism-armor only reduce an already super-effective hit`() {
        val effects = listOf(AbilityEffect.SuperEffectiveMultiplier(0.75))
        assertEquals(1.5, applyAbilityEffects(2.0, PokemonType.FIRE, effects), 0.0)
        assertEquals(3.0, applyAbilityEffects(4.0, PokemonType.FIRE, effects), 0.0)
        // Never-effective and neutral hits are untouched.
        assertEquals(1.0, applyAbilityEffects(1.0, PokemonType.FIRE, effects), 0.0)
        assertEquals(0.5, applyAbilityEffects(0.5, PokemonType.FIRE, effects), 0.0)
    }

    @Test
    fun `applyAbilityEffects delta-stream caps a super-effective hit at neutral`() {
        val effects = listOf(AbilityEffect.NeverSuperEffective)
        assertEquals(1.0, applyAbilityEffects(2.0, PokemonType.ELECTRIC, effects), 0.0)
        assertEquals(1.0, applyAbilityEffects(4.0, PokemonType.ELECTRIC, effects), 0.0)
        // A resisted or neutral hit is untouched — the cap only ever lowers, never raises.
        assertEquals(0.5, applyAbilityEffects(0.5, PokemonType.ELECTRIC, effects), 0.0)
    }

    @Test
    fun `applyAbilityEffects badge-only never changes the multiplier`() {
        val effects = listOf(AbilityEffect.BadgeOnly("note"))
        assertEquals(2.0, applyAbilityEffects(2.0, PokemonType.FIRE, effects), 0.0)
    }

    // --- overriddenMoveType / bypassesGhostImmunity (offensive gap, §7.2) ---

    @Test
    fun `overriddenMoveType rewrites only a Normal-type move, for the -ate abilities`() {
        assertEquals(PokemonType.ICE, overriddenMoveType("refrigerate", PokemonType.NORMAL))
        assertEquals(PokemonType.FAIRY, overriddenMoveType("pixilate", PokemonType.NORMAL))
        assertEquals(PokemonType.FLYING, overriddenMoveType("aerilate", PokemonType.NORMAL))
        assertEquals(PokemonType.ELECTRIC, overriddenMoveType("galvanize", PokemonType.NORMAL))
        // A non-Normal move is untouched by any of them.
        assertEquals(PokemonType.WATER, overriddenMoveType("refrigerate", PokemonType.WATER))
    }

    @Test
    fun `overriddenMoveType normalize rewrites every move, not just Normal ones`() {
        assertEquals(PokemonType.NORMAL, overriddenMoveType("normalize", PokemonType.WATER))
        assertEquals(PokemonType.NORMAL, overriddenMoveType("normalize", PokemonType.NORMAL))
    }

    @Test
    fun `overriddenMoveType is a no-op for null, empty or unrelated abilities`() {
        assertEquals(PokemonType.WATER, overriddenMoveType(null, PokemonType.WATER))
        assertEquals(PokemonType.WATER, overriddenMoveType("", PokemonType.WATER))
        assertEquals(PokemonType.NORMAL, overriddenMoveType("intimidate", PokemonType.NORMAL))
    }

    @Test
    fun `bypassesGhostImmunity is true only for scrappy and mind's eye`() {
        assertTrue(bypassesGhostImmunity("scrappy"))
        assertTrue(bypassesGhostImmunity("Mind's Eye"))
        assertFalse(bypassesGhostImmunity("intimidate"))
        assertFalse(bypassesGhostImmunity(null))
    }
}
