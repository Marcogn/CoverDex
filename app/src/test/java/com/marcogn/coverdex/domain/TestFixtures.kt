package com.marcogn.coverdex.domain

import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart

/**
 * Ported from `legacy-web/src/utils/__tests__/testFixtures.ts` — the oracle for the coverage,
 * suggestion and generator engines' Kotlin tests (`docs/plan/README.md`, "Behavioural parity").
 * Values are copied field-for-field, not re-derived, so a Kotlin test can assert the exact same
 * expected numbers as its TypeScript twin.
 */

/** Hardcoded canonical Gen 6+ type chart. Rows are attacker, columns are defender, values in
 * {0, 0.5, 1, 2} — matches `buildTypeChart()` in the TS fixtures exactly. */
fun mockTypeChart(): TypeChart {
    val table = mutableMapOf<PokemonType, MutableMap<PokemonType, Double>>()
    for (a in PokemonType.entries) {
        table[a] = PokemonType.entries.associateWithTo(mutableMapOf()) { 1.0 }
    }
    fun set(a: PokemonType, d: PokemonType, v: Double) {
        table.getValue(a)[d] = v
    }

    // Normal
    set(PokemonType.NORMAL, PokemonType.ROCK, 0.5)
    set(PokemonType.NORMAL, PokemonType.GHOST, 0.0)
    set(PokemonType.NORMAL, PokemonType.STEEL, 0.5)
    // Fire
    set(PokemonType.FIRE, PokemonType.FIRE, 0.5)
    set(PokemonType.FIRE, PokemonType.WATER, 0.5)
    set(PokemonType.FIRE, PokemonType.GRASS, 2.0)
    set(PokemonType.FIRE, PokemonType.ICE, 2.0)
    set(PokemonType.FIRE, PokemonType.BUG, 2.0)
    set(PokemonType.FIRE, PokemonType.ROCK, 0.5)
    set(PokemonType.FIRE, PokemonType.DRAGON, 0.5)
    set(PokemonType.FIRE, PokemonType.STEEL, 2.0)
    // Water
    set(PokemonType.WATER, PokemonType.FIRE, 2.0)
    set(PokemonType.WATER, PokemonType.WATER, 0.5)
    set(PokemonType.WATER, PokemonType.GRASS, 0.5)
    set(PokemonType.WATER, PokemonType.GROUND, 2.0)
    set(PokemonType.WATER, PokemonType.ROCK, 2.0)
    set(PokemonType.WATER, PokemonType.DRAGON, 0.5)
    // Electric
    set(PokemonType.ELECTRIC, PokemonType.WATER, 2.0)
    set(PokemonType.ELECTRIC, PokemonType.ELECTRIC, 0.5)
    set(PokemonType.ELECTRIC, PokemonType.GRASS, 0.5)
    set(PokemonType.ELECTRIC, PokemonType.GROUND, 0.0)
    set(PokemonType.ELECTRIC, PokemonType.FLYING, 2.0)
    set(PokemonType.ELECTRIC, PokemonType.DRAGON, 0.5)
    // Grass
    set(PokemonType.GRASS, PokemonType.FIRE, 0.5)
    set(PokemonType.GRASS, PokemonType.WATER, 2.0)
    set(PokemonType.GRASS, PokemonType.GRASS, 0.5)
    set(PokemonType.GRASS, PokemonType.POISON, 0.5)
    set(PokemonType.GRASS, PokemonType.GROUND, 2.0)
    set(PokemonType.GRASS, PokemonType.FLYING, 0.5)
    set(PokemonType.GRASS, PokemonType.BUG, 0.5)
    set(PokemonType.GRASS, PokemonType.ROCK, 2.0)
    set(PokemonType.GRASS, PokemonType.DRAGON, 0.5)
    set(PokemonType.GRASS, PokemonType.STEEL, 0.5)
    // Ice
    set(PokemonType.ICE, PokemonType.FIRE, 0.5)
    set(PokemonType.ICE, PokemonType.WATER, 0.5)
    set(PokemonType.ICE, PokemonType.GRASS, 2.0)
    set(PokemonType.ICE, PokemonType.ICE, 0.5)
    set(PokemonType.ICE, PokemonType.GROUND, 2.0)
    set(PokemonType.ICE, PokemonType.FLYING, 2.0)
    set(PokemonType.ICE, PokemonType.DRAGON, 2.0)
    set(PokemonType.ICE, PokemonType.STEEL, 0.5)
    // Fighting
    set(PokemonType.FIGHTING, PokemonType.NORMAL, 2.0)
    set(PokemonType.FIGHTING, PokemonType.ICE, 2.0)
    set(PokemonType.FIGHTING, PokemonType.POISON, 0.5)
    set(PokemonType.FIGHTING, PokemonType.FLYING, 0.5)
    set(PokemonType.FIGHTING, PokemonType.PSYCHIC, 0.5)
    set(PokemonType.FIGHTING, PokemonType.BUG, 0.5)
    set(PokemonType.FIGHTING, PokemonType.ROCK, 2.0)
    set(PokemonType.FIGHTING, PokemonType.GHOST, 0.0)
    set(PokemonType.FIGHTING, PokemonType.DARK, 2.0)
    set(PokemonType.FIGHTING, PokemonType.STEEL, 2.0)
    set(PokemonType.FIGHTING, PokemonType.FAIRY, 0.5)
    // Poison
    set(PokemonType.POISON, PokemonType.GRASS, 2.0)
    set(PokemonType.POISON, PokemonType.POISON, 0.5)
    set(PokemonType.POISON, PokemonType.GROUND, 0.5)
    set(PokemonType.POISON, PokemonType.ROCK, 0.5)
    set(PokemonType.POISON, PokemonType.GHOST, 0.5)
    set(PokemonType.POISON, PokemonType.STEEL, 0.0)
    set(PokemonType.POISON, PokemonType.FAIRY, 2.0)
    // Ground
    set(PokemonType.GROUND, PokemonType.FIRE, 2.0)
    set(PokemonType.GROUND, PokemonType.ELECTRIC, 2.0)
    set(PokemonType.GROUND, PokemonType.GRASS, 0.5)
    set(PokemonType.GROUND, PokemonType.POISON, 2.0)
    set(PokemonType.GROUND, PokemonType.FLYING, 0.0)
    set(PokemonType.GROUND, PokemonType.BUG, 0.5)
    set(PokemonType.GROUND, PokemonType.ROCK, 2.0)
    set(PokemonType.GROUND, PokemonType.STEEL, 2.0)
    // Flying
    set(PokemonType.FLYING, PokemonType.ELECTRIC, 0.5)
    set(PokemonType.FLYING, PokemonType.GRASS, 2.0)
    set(PokemonType.FLYING, PokemonType.FIGHTING, 2.0)
    set(PokemonType.FLYING, PokemonType.BUG, 2.0)
    set(PokemonType.FLYING, PokemonType.ROCK, 0.5)
    set(PokemonType.FLYING, PokemonType.STEEL, 0.5)
    // Psychic
    set(PokemonType.PSYCHIC, PokemonType.FIGHTING, 2.0)
    set(PokemonType.PSYCHIC, PokemonType.POISON, 2.0)
    set(PokemonType.PSYCHIC, PokemonType.PSYCHIC, 0.5)
    set(PokemonType.PSYCHIC, PokemonType.DARK, 0.0)
    set(PokemonType.PSYCHIC, PokemonType.STEEL, 0.5)
    // Bug
    set(PokemonType.BUG, PokemonType.FIRE, 0.5)
    set(PokemonType.BUG, PokemonType.GRASS, 2.0)
    set(PokemonType.BUG, PokemonType.FIGHTING, 0.5)
    set(PokemonType.BUG, PokemonType.POISON, 0.5)
    set(PokemonType.BUG, PokemonType.FLYING, 0.5)
    set(PokemonType.BUG, PokemonType.PSYCHIC, 2.0)
    set(PokemonType.BUG, PokemonType.GHOST, 0.5)
    set(PokemonType.BUG, PokemonType.DARK, 2.0)
    set(PokemonType.BUG, PokemonType.STEEL, 0.5)
    set(PokemonType.BUG, PokemonType.FAIRY, 0.5)
    // Rock
    set(PokemonType.ROCK, PokemonType.FIRE, 2.0)
    set(PokemonType.ROCK, PokemonType.ICE, 2.0)
    set(PokemonType.ROCK, PokemonType.FIGHTING, 0.5)
    set(PokemonType.ROCK, PokemonType.GROUND, 0.5)
    set(PokemonType.ROCK, PokemonType.FLYING, 2.0)
    set(PokemonType.ROCK, PokemonType.BUG, 2.0)
    set(PokemonType.ROCK, PokemonType.STEEL, 0.5)
    // Ghost
    set(PokemonType.GHOST, PokemonType.NORMAL, 0.0)
    set(PokemonType.GHOST, PokemonType.PSYCHIC, 2.0)
    set(PokemonType.GHOST, PokemonType.GHOST, 2.0)
    set(PokemonType.GHOST, PokemonType.DARK, 0.5)
    // Dragon
    set(PokemonType.DRAGON, PokemonType.DRAGON, 2.0)
    set(PokemonType.DRAGON, PokemonType.STEEL, 0.5)
    set(PokemonType.DRAGON, PokemonType.FAIRY, 0.0)
    // Dark
    set(PokemonType.DARK, PokemonType.FIGHTING, 0.5)
    set(PokemonType.DARK, PokemonType.PSYCHIC, 2.0)
    set(PokemonType.DARK, PokemonType.GHOST, 2.0)
    set(PokemonType.DARK, PokemonType.DARK, 0.5)
    set(PokemonType.DARK, PokemonType.FAIRY, 0.5)
    // Steel
    set(PokemonType.STEEL, PokemonType.FIRE, 0.5)
    set(PokemonType.STEEL, PokemonType.WATER, 0.5)
    set(PokemonType.STEEL, PokemonType.ELECTRIC, 0.5)
    set(PokemonType.STEEL, PokemonType.ICE, 2.0)
    set(PokemonType.STEEL, PokemonType.ROCK, 2.0)
    set(PokemonType.STEEL, PokemonType.STEEL, 0.5)
    set(PokemonType.STEEL, PokemonType.FAIRY, 2.0)
    // Fairy
    set(PokemonType.FAIRY, PokemonType.FIRE, 0.5)
    set(PokemonType.FAIRY, PokemonType.FIGHTING, 2.0)
    set(PokemonType.FAIRY, PokemonType.POISON, 0.5)
    set(PokemonType.FAIRY, PokemonType.DRAGON, 2.0)
    set(PokemonType.FAIRY, PokemonType.DARK, 2.0)
    set(PokemonType.FAIRY, PokemonType.STEEL, 0.5)

    return TypeChart(table)
}

/** Matches the TS fixtures' `buildMember`: up to 4 move types (special, power 80), the rest left
 * empty. [id] defaults to `member-$speciesName`, overridable for tests that need a stable id
 * across a type-override edit. */
fun buildMember(
    speciesName: String,
    types: Pair<PokemonType, PokemonType?>,
    moveTypes: List<PokemonType> = emptyList(),
    id: String = "member-$speciesName",
    ability: String? = null,
    isCustomSaved: Boolean = false,
): TeamMember {
    val moves = MutableList<PokemonMove?>(4) { null }
    moveTypes.take(4).forEachIndexed { i, mt ->
        moves[i] = PokemonMove(
            id = "mv-$speciesName-$i",
            name = "${mt.apiName}-move",
            type = mt,
            power = 80,
            damageClass = DamageClass.SPECIAL,
            isCustom = false,
        )
    }
    return TeamMember(
        id = id,
        pokedexId = null,
        speciesName = speciesName,
        types = types,
        ability = ability,
        moves = moves,
        isCustomSaved = isCustomSaved,
    )
}

/**
 * Ported from the TS fixtures' `mockPokemonList` (10 entries) field-for-field, for
 * `SuggestionEngineTest`/`TeamGeneratorTest`. `generationIntroduced` and `speciesId` have no TS
 * equivalent (Kotlin's [PokemonEntry] stores the real generation and species id Phase 1 syncs,
 * where the TS mock only ever carried an `id`); real Pokédex generations are used for every real
 * species, and Spectraform — the mock's one fictional, alternate-form-like entry
 * (`id = 9301`, `> 10000`-style id used to model an alt form for suggestion tests) — is
 * deliberately given `generationIntroduced = 1` despite its high id, to exercise the one
 * intentional deviation from `suggestionEngine.ts` this phase makes: filtering by the real
 * generation instead of `GEN_RANGES` id buckets (`docs/plan/phase-4-suggestions-and-generator.md`
 * §2). Under the old id-bucket scheme Spectraform would have wrongly landed in "Generation 9".
 */
fun mockPokemonList(): List<PokemonEntry> = listOf(
    PokemonEntry(
        id = 143, name = "snorlax", displayName = "Snorlax", speciesId = 143, speciesName = "snorlax",
        types = PokemonType.NORMAL to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 130, name = "gyarados", displayName = "Gyarados", speciesId = 130, speciesName = "gyarados",
        types = PokemonType.WATER to PokemonType.FLYING, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 92, name = "gastly", displayName = "Gastly", speciesId = 92, speciesName = "gastly",
        types = PokemonType.GHOST to null, isLegendary = false, isMythical = false, isFinalEvolution = false,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 9301, name = "spectraform", displayName = "Spectraform", speciesId = 9301, speciesName = "spectraform",
        types = PokemonType.GHOST to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 303, name = "mawile", displayName = "Mawile", speciesId = 303, speciesName = "mawile",
        types = PokemonType.STEEL to PokemonType.FAIRY, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 3, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 6, name = "charizard", displayName = "Charizard", speciesId = 6, speciesName = "charizard",
        types = PokemonType.FIRE to PokemonType.FLYING, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 25, name = "pikachu", displayName = "Pikachu", speciesId = 25, speciesName = "pikachu",
        types = PokemonType.ELECTRIC to null, isLegendary = false, isMythical = false, isFinalEvolution = false,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 445, name = "garchomp", displayName = "Garchomp", speciesId = 445, speciesName = "garchomp",
        types = PokemonType.DRAGON to PokemonType.GROUND, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 4, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 700, name = "sylveon", displayName = "Sylveon", speciesId = 700, speciesName = "sylveon",
        types = PokemonType.FAIRY to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 6, defaultAbility = null, isDefaultForm = true,
    ),
    PokemonEntry(
        id = 150, name = "mewtwo", displayName = "Mewtwo", speciesId = 150, speciesName = "mewtwo",
        types = PokemonType.PSYCHIC to null, isLegendary = true, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
    ),
)
