package com.marcogn.coverdex.domain.item

import com.marcogn.coverdex.domain.model.PokemonType

/**
 * Held items are otherwise unmodelled in this app (no field existed before Phase 7 at all) — see
 * docs/plan/phase-7-accuracy-and-customization.md §4. Modelled here only for the items that
 * change a type multiplier; everything else is free text with no calculation effect, same
 * "type it, nothing rejects it" contract as `domain/ability/AbilityEffects.kt`. Deliberately not
 * modelled, with the reason: Heavy-Duty Boots and Utility Umbrella (entry hazards / weather —
 * neither touches a type multiplier), Expert Belt and the type-boosting plates/gems (offensive
 * damage, not the >=2x coverage threshold `offensiveCoverageForMember` tests).
 */
sealed interface ItemEffect {
    /** Air Balloon: Ground moves miss entirely, until the balloon pops — modelled here as an
     * unconditional immunity, since this engine has no "already been hit this turn" concept. */
    data class Immunity(val type: PokemonType) : ItemEffect

    /** Iron Ball: grounds the holder, cancelling a Ground-move immunity from any source
     * (Levitate, Air Balloon, the Flying type itself, Ground's own type-chart immunities elsewhere)
     * — but only for Ground moves; every other immunity the holder has is untouched. */
    data object GroundsHolder : ItemEffect

    /** Ring Target: removes every type immunity the holder has, of any type, from any source —
     * broader than Iron Ball, which only cancels Ground. */
    data object RemovesTypeImmunities : ItemEffect

    /** A type-resist berry: halves an incoming hit of [type] once it is already super-effective
     * (>1x) — a real resist berry is also consumed on that hit, which this engine has no concept
     * of, so the halving is modelled as permanent. [alwaysApplies] is Chilan Berry's own
     * exception: it halves Normal damage unconditionally, super-effective or not, since Normal
     * has no super-effective matchups to gate on. */
    data class ResistBerry(val type: PokemonType, val alwaysApplies: Boolean = false) : ItemEffect
}

/** Ported from PokéAPI's own item data: one resist berry per type (Chilan Berry covers Normal,
 * unconditionally, since nothing is super-effective against Normal), plus Air Balloon, Iron Ball
 * and Ring Target. Keyed the same way as `ABILITY_EFFECTS` — lowercase, hyphenated, matching
 * PokéAPI's item `identifier` — and looked up the same symbol-insensitive way via [itemKey]. */
val ITEM_EFFECTS: Map<String, List<ItemEffect>> = mapOf(
    "air-balloon" to listOf(ItemEffect.Immunity(PokemonType.GROUND)),
    "iron-ball" to listOf(ItemEffect.GroundsHolder),
    "ring-target" to listOf(ItemEffect.RemovesTypeImmunities),
    "occa-berry" to listOf(ItemEffect.ResistBerry(PokemonType.FIRE)),
    "passho-berry" to listOf(ItemEffect.ResistBerry(PokemonType.WATER)),
    "wacan-berry" to listOf(ItemEffect.ResistBerry(PokemonType.ELECTRIC)),
    "rindo-berry" to listOf(ItemEffect.ResistBerry(PokemonType.GRASS)),
    "yache-berry" to listOf(ItemEffect.ResistBerry(PokemonType.ICE)),
    "chople-berry" to listOf(ItemEffect.ResistBerry(PokemonType.FIGHTING)),
    "kebia-berry" to listOf(ItemEffect.ResistBerry(PokemonType.POISON)),
    "shuca-berry" to listOf(ItemEffect.ResistBerry(PokemonType.GROUND)),
    "coba-berry" to listOf(ItemEffect.ResistBerry(PokemonType.FLYING)),
    "payapa-berry" to listOf(ItemEffect.ResistBerry(PokemonType.PSYCHIC)),
    "tanga-berry" to listOf(ItemEffect.ResistBerry(PokemonType.BUG)),
    "charti-berry" to listOf(ItemEffect.ResistBerry(PokemonType.ROCK)),
    "kasib-berry" to listOf(ItemEffect.ResistBerry(PokemonType.GHOST)),
    "haban-berry" to listOf(ItemEffect.ResistBerry(PokemonType.DRAGON)),
    "colbur-berry" to listOf(ItemEffect.ResistBerry(PokemonType.DARK)),
    "babiri-berry" to listOf(ItemEffect.ResistBerry(PokemonType.STEEL)),
    "roseli-berry" to listOf(ItemEffect.ResistBerry(PokemonType.FAIRY)),
    "chilan-berry" to listOf(ItemEffect.ResistBerry(PokemonType.NORMAL, alwaysApplies = true)),
)

/** Lowercase, letters and digits only — mirrors
 * [com.marcogn.coverdex.domain.ability.abilityKey]/[com.marcogn.coverdex.domain.pokeapi.searchKey],
 * so `"Air Balloon"`, `"air-balloon"` and `"airballoon"` all resolve to the same [ITEM_EFFECTS]
 * entry. */
fun itemKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

private val effectsBySymbolFreeKey: Map<String, List<ItemEffect>> = ITEM_EFFECTS.mapKeys { (slug, _) -> itemKey(slug) }

/** `null` or empty returns `null`, same falsy contract as
 * [com.marcogn.coverdex.domain.ability.getAbilityEffects] — an item genuinely absent from
 * [ITEM_EFFECTS] (anything not in the defensive subset, or free text) is indistinguishable from
 * "no item" here, which is correct: neither has a calculation effect. */
fun getItemEffects(item: String?): List<ItemEffect>? {
    if (item.isNullOrEmpty()) return null
    return effectsBySymbolFreeKey[itemKey(item)]
}
