package com.marcogn.coverdex.data.local.entity

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
