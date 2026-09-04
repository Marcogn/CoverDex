package com.marcogn.coverdex.domain.model

/**
 * The 18 Pokémon types. [id] matches the pinned dataset's `types.csv` id column (1-18) exactly —
 * see docs/plan/reference-pokedata.md §5, which verifies these are the same ids
 * legacy-web/src/data/typeSprites.ts uses for its type-badge sprite URLs. `types.csv` also has
 * ids 19 (stellar), 10001 (unknown) and 10002 (shadow); those never enter the app.
 */
enum class PokemonType(val apiName: String, val id: Int) {
    NORMAL("normal", 1),
    FIGHTING("fighting", 2),
    FLYING("flying", 3),
    POISON("poison", 4),
    GROUND("ground", 5),
    ROCK("rock", 6),
    BUG("bug", 7),
    GHOST("ghost", 8),
    STEEL("steel", 9),
    FIRE("fire", 10),
    WATER("water", 11),
    GRASS("grass", 12),
    ELECTRIC("electric", 13),
    PSYCHIC("psychic", 14),
    ICE("ice", 15),
    DRAGON("dragon", 16),
    DARK("dark", 17),
    FAIRY("fairy", 18);

    companion object {
        fun fromApiName(name: String): PokemonType? = entries.find { it.apiName == name }
        fun fromId(id: Int): PokemonType? = entries.find { it.id == id }
    }
}
