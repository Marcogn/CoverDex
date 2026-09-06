package com.marcogn.coverdex.domain.ability

import com.marcogn.coverdex.domain.model.PokemonType

/** Ported originally from `legacy-web/src/data/abilityEffects.ts`'s `AbilityEffect` union (that
 * directory was deleted in Phase 6 — see CLAUDE.md, "Sibling projects"); extended in Phase 7
 * (docs/plan/phase-7-accuracy-and-customization.md §7.1) with the variants the original TypeScript
 * never needed. Adding or removing an entry anywhere in this file is a spec change, not an
 * implementation detail. */
enum class AbilityEffectSide { OFFENSIVE, DEFENSIVE }

sealed interface AbilityEffect {
    data class Immunity(val type: PokemonType) : AbilityEffect
    data class Multiplier(val type: PokemonType, val factor: Double, val side: AbilityEffectSide) : AbilityEffect
    data class BadgeOnly(val note: String) : AbilityEffect

    /** Filter / Solid Rock / Prism Armor: multiplies an already-super-effective (>1x) incoming
     * hit by [factor]. A no-op at 1x or below — see [applyAbilityEffects]. */
    data class SuperEffectiveMultiplier(val factor: Double) : AbilityEffect

    /** Delta Stream: nothing is super-effective against the holder while its ability holds —
     * caps an incoming multiplier at 1.0. */
    data object NeverSuperEffective : AbilityEffect

    /** Wonder Guard: only a super-effective (>1x) hit deals any damage at all — every other
     * multiplier, resistances and neutral hits alike, becomes 0. Promoted from [BadgeOnly] in
     * Phase 7: leaving Shedinja's signature ability as a UI-only badge made `defensiveProfile`
     * actively wrong for the one Pokemon it applies to (phase-7-...md §7.1). */
    data object OnlySuperEffective : AbilityEffect
}

/**
 * Hardcoded map of ability slugs (lowercase, hyphenated, matching PokéAPI) to their
 * coverage-relevant effects. Only abilities that alter defensive/offensive type effectiveness or
 * warrant a UI badge are included here.
 *
 * The ten entries from `heatproof` through `tera-shell`, and the `well-baked-body`
 * fix-up to `wonder-guard`'s promotion, were added in Phase 7 after auditing every ability's
 * `short_effect` text from the pinned dataset's `ability_prose.csv` — see
 * docs/plan/phase-7-accuracy-and-customization.md §0.4/§7.1 for the sourcing and the forms-count
 * evidence. `tinted-lens` and `neuroforce` are deliberately absent: neither moves a multiplier
 * across the >=2x threshold [com.marcogn.coverdex.domain.coverage.offensiveCoverageForMember]
 * tests, so neither changes coverage, and this table only models effects that do.
 * `primordial-sea`/`desolate-land` are real field effects that apply to both sides in the actual
 * games; modelled here as the holder's own immunity only, since this app has no weather/field
 * concept. `tera-shell` stays [AbilityEffect.BadgeOnly]: it is unconditional only at full HP, and
 * this engine has no HP concept, so modelling it as an always-on multiplier would be wrong more
 * often than right.
 */
val ABILITY_EFFECTS: Map<String, List<AbilityEffect>> = mapOf(
    // Immunities (defensive — incoming moves of that type deal 0)
    "volt-absorb" to listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)),
    "lightning-rod" to listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)),
    "motor-drive" to listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)),
    "water-absorb" to listOf(AbilityEffect.Immunity(PokemonType.WATER)),
    "storm-drain" to listOf(AbilityEffect.Immunity(PokemonType.WATER)),
    "flash-fire" to listOf(AbilityEffect.Immunity(PokemonType.FIRE)),
    "sap-sipper" to listOf(AbilityEffect.Immunity(PokemonType.GRASS)),
    "levitate" to listOf(AbilityEffect.Immunity(PokemonType.GROUND)),
    "earth-eater" to listOf(AbilityEffect.Immunity(PokemonType.GROUND)),
    "well-baked-body" to listOf(AbilityEffect.Immunity(PokemonType.FIRE)),
    "primordial-sea" to listOf(AbilityEffect.Immunity(PokemonType.FIRE)),
    "desolate-land" to listOf(AbilityEffect.Immunity(PokemonType.WATER)),
    // Dry Skin: absorbs Water (immune) but takes 1.25x from Fire — both halves of its real
    // effect, unlike the pre-Phase-7 table which only had the immunity.
    "dry-skin" to listOf(
        AbilityEffect.Immunity(PokemonType.WATER),
        AbilityEffect.Multiplier(PokemonType.FIRE, 1.25, AbilityEffectSide.DEFENSIVE),
    ),
    // Multiplier (defensive — modifies effective damage multiplier received)
    "thick-fat" to listOf(
        AbilityEffect.Multiplier(PokemonType.FIRE, 0.5, AbilityEffectSide.DEFENSIVE),
        AbilityEffect.Multiplier(PokemonType.ICE, 0.5, AbilityEffectSide.DEFENSIVE),
    ),
    "fluffy" to listOf(AbilityEffect.Multiplier(PokemonType.FIRE, 2.0, AbilityEffectSide.DEFENSIVE)),
    "heatproof" to listOf(AbilityEffect.Multiplier(PokemonType.FIRE, 0.5, AbilityEffectSide.DEFENSIVE)),
    "water-bubble" to listOf(AbilityEffect.Multiplier(PokemonType.FIRE, 0.5, AbilityEffectSide.DEFENSIVE)),
    "purifying-salt" to listOf(AbilityEffect.Multiplier(PokemonType.GHOST, 0.5, AbilityEffectSide.DEFENSIVE)),
    // Super-effective reducers (defensive — only bite once a hit is already >1x)
    "filter" to listOf(AbilityEffect.SuperEffectiveMultiplier(0.75)),
    "solid-rock" to listOf(AbilityEffect.SuperEffectiveMultiplier(0.75)),
    "prism-armor" to listOf(AbilityEffect.SuperEffectiveMultiplier(0.75)),
    "delta-stream" to listOf(AbilityEffect.NeverSuperEffective),
    // Badge-only (no calculation change)
    "tera-shell" to listOf(AbilityEffect.BadgeOnly("Not very effective at full HP")),
    "wonder-guard" to listOf(AbilityEffect.OnlySuperEffective),
)

/** Ability slugs (PokéAPI identifier format, e.g. `"sap-sipper"`) that have a coverage-relevant
 * effect, in ability-picker display format (spaces instead of hyphens) — regenerated from
 * [ABILITY_EFFECTS] rather than hand-maintained a second time, so it cannot drift from the actual
 * effect table (phase-7-accuracy-and-customization.md §7.1). Not currently read by any UI; kept
 * for whatever surface wants a flat "which abilities matter" list without inspecting
 * [ABILITY_EFFECTS] itself — the ability picker's own "has an effect" badge
 * (`ui/team/SlotEditorScreen.kt`) checks `abilityKey(name) in ABILITY_EFFECTS` directly instead. */
val KNOWN_ABILITIES_WITH_EFFECTS: List<String> = ABILITY_EFFECTS.keys.map { it.replace('-', ' ') }

/** Lowercase, letters and digits only — so `"Well-Baked Body"`, `"well-baked-body"` and
 * `"wellbakedbody"` all resolve to the same [ABILITY_EFFECTS] entry, mirroring
 * [com.marcogn.coverdex.domain.pokeapi.searchKey]. Replaces the pre-Phase-7 `normalizeAbilityName`
 * (space-to-hyphen only), which could not match a display name that keeps a hyphen the slug
 * doesn't have or vice versa — see docs/plan/phase-7-accuracy-and-customization.md §0.2/§3.1.
 * [ABILITY_EFFECTS]'s own keys are hyphenated slugs, so this function is also applied to them at
 * lookup time via [normalizedEffectsBySymbolFreeKey]. */
fun abilityKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

private val normalizedEffectsBySymbolFreeKey: Map<String, List<AbilityEffect>> =
    ABILITY_EFFECTS.mapKeys { (slug, _) -> abilityKey(slug) }

/** Look up the effects for a given ability name — a raw PokéAPI slug, a display name, or
 * anything symbol-equivalent to one (see [abilityKey]). `null` or empty (not merely blank —
 * matches the pre-Phase-7 `!ability` falsy check exactly) returns `null`, same as an ability
 * genuinely absent from [ABILITY_EFFECTS] (a ROM hack's custom ability, or a canonical ability
 * with no coverage effect). */
fun getAbilityEffects(ability: String?): List<AbilityEffect>? {
    if (ability.isNullOrEmpty()) return null
    return normalizedEffectsBySymbolFreeKey[abilityKey(ability)]
}

/**
 * Applies every effect in [effects] to a chart-derived multiplier, in the fixed order
 * docs/plan/phase-7-accuracy-and-customization.md §4.2 specifies for items (abilities apply the
 * same steps, minus the item-only ones): immunities and [AbilityEffect.OnlySuperEffective] short
 * circuit first (an incoming hit is either fully blocked or, for Wonder Guard, everything *but* a
 * super-effective hit is), then plain multipliers, then super-effective-only reducers/caps. Pure
 * arithmetic — no ordering-sensitive early return except the two variants that are absolute
 * (immunity, Wonder Guard), so calling this with an empty or `null`-derived effect list is always
 * a safe no-op.
 */
fun applyAbilityEffects(baseMultiplier: Double, attackingType: PokemonType, effects: List<AbilityEffect>?): Double {
    if (effects.isNullOrEmpty()) return baseMultiplier

    for (effect in effects) {
        if (effect is AbilityEffect.Immunity && effect.type == attackingType) return 0.0
    }
    if (effects.any { it is AbilityEffect.OnlySuperEffective }) {
        return if (baseMultiplier > 1.0) baseMultiplier else 0.0
    }

    var result = baseMultiplier
    for (effect in effects) {
        if (effect is AbilityEffect.Multiplier && effect.side == AbilityEffectSide.DEFENSIVE && effect.type == attackingType) {
            result *= effect.factor
        }
    }
    if (result > 1.0) {
        for (effect in effects) {
            when (effect) {
                is AbilityEffect.SuperEffectiveMultiplier -> result *= effect.factor
                AbilityEffect.NeverSuperEffective -> result = 1.0
                else -> Unit
            }
        }
    }
    return result
}

/** Types this ability's canonical `-ate`/Normalize effect rewrites a move's own type to — a
 * Normal-type move only, except Normalize, which rewrites every move. `null` ability or one with
 * no such effect returns [moveType] unchanged. Offensive gap closed in Phase 7, see
 * docs/plan/phase-7-accuracy-and-customization.md §7.2; `liquid-voice` (sound-based moves become
 * Water) is deliberately not modelled here — this app has no move-flag data to know which moves
 * are sound-based. */
fun overriddenMoveType(ability: String?, moveType: PokemonType): PokemonType {
    if (ability.isNullOrEmpty()) return moveType
    val key = abilityKey(ability)
    if (key == "normalize") return PokemonType.NORMAL
    if (moveType != PokemonType.NORMAL) return moveType
    return when (key) {
        "refrigerate" -> PokemonType.ICE
        "pixilate" -> PokemonType.FAIRY
        "aerilate" -> PokemonType.FLYING
        "galvanize" -> PokemonType.ELECTRIC
        else -> moveType
    }
}

/** Scrappy/Mind's Eye: the holder's Normal and Fighting moves hit a Ghost-type defender
 * neutrally instead of being blocked outright. Never changes [PokemonType.entries]-scanning
 * *coverage* (going from 0x to 1x never crosses the >=2x threshold
 * [com.marcogn.coverdex.domain.coverage.offensiveCoverageForMember] tests) — only the offensive
 * grid, which shows the real multiplier per type. See
 * docs/plan/phase-7-accuracy-and-customization.md §7.2. */
fun bypassesGhostImmunity(ability: String?): Boolean =
    !ability.isNullOrEmpty() && abilityKey(ability) in setOf("scrappy", "mindseye")
