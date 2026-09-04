package com.marcogn.coverdex.domain.ability

import com.marcogn.coverdex.domain.model.PokemonType

/** Verbatim port of `legacy-web/src/data/abilityEffects.ts`'s `AbilityEffect` union — adding or
 * removing an entry anywhere in this file is a spec change, not an implementation detail (see
 * `docs/plan/phase-3-analysis.md` §1). */
enum class AbilityEffectSide { OFFENSIVE, DEFENSIVE }

sealed interface AbilityEffect {
    data class Immunity(val type: PokemonType) : AbilityEffect
    data class Multiplier(val type: PokemonType, val factor: Double, val side: AbilityEffectSide) : AbilityEffect
    data class BadgeOnly(val note: String) : AbilityEffect
}

/** Canonical list of abilities with known coverage effects, used by the ability picker UI. Names
 * are in display format (lowercase, space-separated) — matches the TS list verbatim. */
val KNOWN_ABILITIES_WITH_EFFECTS: List<String> = listOf(
    "volt absorb",
    "lightning rod",
    "motor drive",
    "water absorb",
    "storm drain",
    "dry skin",
    "flash fire",
    "sap sipper",
    "levitate",
    "earth eater",
    "well-baked body",
    "thick fat",
    "fluffy",
    "wonder guard",
)

/** Hardcoded map of ability slugs (lowercase, hyphenated, matching PokéAPI) to their
 * coverage-relevant effects. Only abilities that alter defensive multipliers or warrant a UI
 * badge are included here — verbatim port of `ABILITY_EFFECTS`. */
val ABILITY_EFFECTS: Map<String, List<AbilityEffect>> = mapOf(
    // Immunities (defensive — incoming moves of that type deal 0)
    "volt-absorb" to listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)),
    "lightning-rod" to listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)),
    "motor-drive" to listOf(AbilityEffect.Immunity(PokemonType.ELECTRIC)),
    "water-absorb" to listOf(AbilityEffect.Immunity(PokemonType.WATER)),
    "storm-drain" to listOf(AbilityEffect.Immunity(PokemonType.WATER)),
    "dry-skin" to listOf(AbilityEffect.Immunity(PokemonType.WATER)),
    "flash-fire" to listOf(AbilityEffect.Immunity(PokemonType.FIRE)),
    "sap-sipper" to listOf(AbilityEffect.Immunity(PokemonType.GRASS)),
    "levitate" to listOf(AbilityEffect.Immunity(PokemonType.GROUND)),
    "earth-eater" to listOf(AbilityEffect.Immunity(PokemonType.GROUND)),
    "well-baked-body" to listOf(AbilityEffect.Immunity(PokemonType.FIRE)),
    // Multiplier (defensive — modifies effective damage multiplier received)
    "thick-fat" to listOf(
        AbilityEffect.Multiplier(PokemonType.FIRE, 0.5, AbilityEffectSide.DEFENSIVE),
        AbilityEffect.Multiplier(PokemonType.ICE, 0.5, AbilityEffectSide.DEFENSIVE),
    ),
    "fluffy" to listOf(AbilityEffect.Multiplier(PokemonType.FIRE, 2.0, AbilityEffectSide.DEFENSIVE)),
    // Badge-only (no calculation change)
    "wonder-guard" to listOf(AbilityEffect.BadgeOnly("Only super-effective moves deal damage")),
)

/** Normalize an ability name to the slug format used as keys in [ABILITY_EFFECTS]. */
fun normalizeAbilityName(name: String): String = name.lowercase().replace(Regex("\\s+"), "-")

/** Look up the effects for a given ability name (case-insensitive, handles spaces). `null` or
 * empty (not merely blank — matches the TS `!ability` falsy check exactly) returns `null`. */
fun getAbilityEffects(ability: String?): List<AbilityEffect>? {
    if (ability.isNullOrEmpty()) return null
    return ABILITY_EFFECTS[normalizeAbilityName(ability)]
}
