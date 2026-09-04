package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.entity.PokeAbilityEntity
import com.marcogn.coverdex.data.local.entity.PokeMoveEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity
import com.marcogn.coverdex.data.local.entity.TypeEfficacyEntity
import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TypeChart

fun PokemonEntry.toEntity(): PokeSpeciesEntity = PokeSpeciesEntity(
    id = id,
    name = name,
    displayName = displayName,
    searchName = com.marcogn.coverdex.domain.pokeapi.searchKey(name),
    speciesId = speciesId,
    speciesName = speciesName,
    type1 = types.first.apiName,
    type2 = types.second?.apiName,
    isLegendary = isLegendary,
    isMythical = isMythical,
    isFinalEvolution = isFinalEvolution,
    generationIntroduced = generationIntroduced,
    defaultAbility = defaultAbility,
    isDefaultForm = isDefaultForm,
)

fun PokeSpeciesEntity.toDomain(): PokemonEntry? {
    val type1 = PokemonType.fromApiName(type1) ?: return null
    val type2 = type2?.let { PokemonType.fromApiName(it) }
    return PokemonEntry(
        id = id,
        name = name,
        displayName = displayName,
        speciesId = speciesId,
        speciesName = speciesName,
        types = type1 to type2,
        isLegendary = isLegendary,
        isMythical = isMythical,
        isFinalEvolution = isFinalEvolution,
        generationIntroduced = generationIntroduced,
        defaultAbility = defaultAbility,
        isDefaultForm = isDefaultForm,
    )
}

fun MoveEntry.toEntity(): PokeMoveEntity = PokeMoveEntity(
    id = id,
    name = name,
    displayName = displayName,
    searchName = com.marcogn.coverdex.domain.pokeapi.searchKey(name),
    typeName = type.apiName,
    power = power,
    damageClass = damageClass.name,
)

fun PokeMoveEntity.toDomain(): MoveEntry? {
    val type = PokemonType.fromApiName(typeName) ?: return null
    val damageClass = runCatching { DamageClass.valueOf(damageClass) }.getOrNull() ?: return null
    return MoveEntry(id = id, name = name, displayName = displayName, type = type, power = power, damageClass = damageClass)
}

fun AbilityEntry.toEntity(): PokeAbilityEntity = PokeAbilityEntity(
    id = id,
    name = name,
    displayName = displayName,
    searchName = com.marcogn.coverdex.domain.pokeapi.searchKey(name),
)

fun PokeAbilityEntity.toDomain(): AbilityEntry = AbilityEntry(id = id, name = name, displayName = displayName)

fun TypeChart.toEntities(): List<TypeEfficacyEntity> =
    entries().map { (attacker, defender, factor) ->
        TypeEfficacyEntity(attacker = attacker.apiName, defender = defender.apiName, factor = factor)
    }

fun List<TypeEfficacyEntity>.toTypeChart(): TypeChart {
    val table = mutableMapOf<PokemonType, MutableMap<PokemonType, Double>>()
    for (row in this) {
        val attacker = PokemonType.fromApiName(row.attacker) ?: continue
        val defender = PokemonType.fromApiName(row.defender) ?: continue
        table.getOrPut(attacker) { mutableMapOf() }[defender] = row.factor
    }
    return TypeChart(table)
}
