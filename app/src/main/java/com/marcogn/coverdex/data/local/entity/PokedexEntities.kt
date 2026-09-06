package com.marcogn.coverdex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The cached-catalogue tables. All five are wiped and rebuilt together, in one transaction, by
 * `PokedexDao.replaceCache()`/`clearCache()` — never through `clearAllTables()`, which would also
 * take the user's teams and roster with it once Phase 2 adds those tables. See
 * docs/plan/reference-pokedata.md §6.
 */
@Entity(tableName = "poke_species", indices = [Index("searchName")])
data class PokeSpeciesEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val displayName: String,
    val searchName: String,
    val speciesId: Int,
    val speciesName: String,
    val type1: String,
    val type2: String?,
    val isLegendary: Boolean,
    val isMythical: Boolean,
    val isFinalEvolution: Boolean,
    val generationIntroduced: Int,
    val defaultAbility: String?,
    val isDefaultForm: Boolean,
    /** Added in Phase 7 by `ALTER TABLE ... ADD COLUMN ... DEFAULT 0` (schema v3) — the default
     * is required so every pre-existing row gets a value, and must be declared here too or
     * `MigrationTestHelper`'s schema validation flags a mismatch. See
     * docs/plan/phase-7-accuracy-and-customization.md §8. */
    @ColumnInfo(defaultValue = "0")
    val baseStatTotal: Int = 0,
)

/** A species form's canonical ability, one row per (form, slot) — added in Phase 7 to back the
 * ability picker's canonical list, see phase-7-accuracy-and-customization.md §3.2/§8. Wiped and
 * rebuilt by `PokedexDao.replaceCache()`/`clearCache()` alongside every other cache table, never
 * by `clearAllTables()`. */
@Entity(tableName = "poke_pokemon_ability", primaryKeys = ["pokemonId", "slot"])
data class PokePokemonAbilityEntity(
    val pokemonId: Int,
    val slot: Int,
    val abilitySlug: String,
    val displayName: String,
    val isHidden: Boolean,
)

/** A form's base stat total as it held through an older generation — only present for the small
 * number of forms whose stats changed across generations; see [PokeSpeciesEntity.baseStatTotal]
 * for the current value and docs/plan/phase-7-accuracy-and-customization.md §2.2 for the
 * generation-1 five-stat rule this backs. Same cache-table lifecycle as
 * [PokePokemonAbilityEntity]. */
@Entity(tableName = "poke_species_bst_past", primaryKeys = ["pokemonId", "generationId"])
data class PokeSpeciesBstPastEntity(
    val pokemonId: Int,
    val generationId: Int,
    val bst: Int,
)

@Entity(tableName = "poke_move", indices = [Index("searchName")])
data class PokeMoveEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val displayName: String,
    val searchName: String,
    val typeName: String,
    val power: Int?,
    val damageClass: String,
)

@Entity(tableName = "poke_ability", indices = [Index("searchName")])
data class PokeAbilityEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val displayName: String,
    val searchName: String,
)

@Entity(tableName = "type_efficacy", primaryKeys = ["attacker", "defender"])
data class TypeEfficacyEntity(
    val attacker: String,
    val defender: String,
    val factor: Double,
)

/** A single row, `id` always 1. See docs/plan/reference-pokedata.md §6 for the invalidation
 * rules this backs: missing row, a `schemaVersion` mismatch or a `datasetRevision` mismatch all
 * mean "cache absent", never a crash. */
@Entity(tableName = "poke_cache_meta")
data class PokeCacheMetaEntity(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int,
    val datasetRevision: String,
    val syncedAtEpochMillis: Long,
    val speciesCount: Int,
    val moveCount: Int,
)
