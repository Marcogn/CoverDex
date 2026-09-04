package com.marcogn.coverdex.domain.pokeapi

import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TypeChart

/** `"mr-mime"` -> `"Mr Mime"`. */
fun prettify(kebabName: String): String =
    kebabName.split("-").joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

/** `"mr-mime"` -> `"mrmime"`, so "mrmime"/"mr mime"/"mr-mime" all match the same search row. */
fun searchKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/** The assembled catalogue — what [com.marcogn.coverdex.data.pokeapi.DatasetSyncManager] writes
 * to the cache tables in one transaction. */
data class ParsedDataset(
    val species: List<PokemonEntry>,
    val moves: List<MoveEntry>,
    val abilities: List<AbilityEntry>,
    val typeChart: TypeChart,
)

/**
 * Joins the 8 pinned CSVs into [ParsedDataset], implementing docs/plan/reference-pokedata.md §3
 * exactly. Pure: no Android imports, no I/O — the caller ([data.pokeapi.DatasetSyncManager])
 * fetches the raw CSV text and hands it here.
 */
fun assembleDataset(
    pokemonCsv: String,
    speciesCsv: String,
    pokemonTypesCsv: String,
    pokemonAbilitiesCsv: String,
    abilitiesCsv: String,
    movesCsv: String,
    typesCsv: String,
    typeEfficacyCsv: String,
): ParsedDataset {
    // types.csv also has ids 19 (stellar), 10001 (unknown) and 10002 (shadow) — filtered out here
    // so nothing downstream ever sees them (docs/plan/reference-pokedata.md §3).
    val typeById: Map<Int, PokemonType> = parseTypes(typesCsv)
        .filter { it.id in 1..18 }
        .mapNotNull { row -> PokemonType.fromApiName(row.identifier)?.let { row.id to it } }
        .toMap()

    val speciesRows = parseSpecies(speciesCsv)
    val speciesById = speciesRows.associateBy { it.id }
    val evolvesFromIds = speciesRows.mapNotNull { it.evolvesFromSpeciesId }.toSet()

    val abilityRows = parseAbilities(abilitiesCsv)
    val abilityIdentifierById = abilityRows.associate { it.id to it.identifier }
    val abilities = abilityRows.map { AbilityEntry(id = it.id, name = it.identifier, displayName = prettify(it.identifier)) }

    val pokemonRows = parsePokemon(pokemonCsv)
    val typesByPokemonId = parsePokemonTypes(pokemonTypesCsv).groupBy { it.pokemonId }
    val abilitiesByPokemonId = parsePokemonAbilities(pokemonAbilitiesCsv).groupBy { it.pokemonId }
    val defaultFormIdBySpeciesId = pokemonRows.filter { it.isDefault }.associate { it.speciesId to it.id }

    fun lowestNonHiddenAbility(pokemonId: Int): String? {
        val row = abilitiesByPokemonId[pokemonId].orEmpty()
            .filter { !it.isHidden }
            .minByOrNull { it.slot }
            ?: return null
        return abilityIdentifierById[row.abilityId]
    }

    // A form with no pokemon_abilities row at all (11 forms, all id >= 10301 as of the pinned
    // revision — unreleased mega forms upstream added ahead of the JSON mirror) falls back to its
    // species' default form, then to null. See docs/plan/reference-pokedata.md §3.
    fun resolveDefaultAbility(pokemonId: Int, speciesId: Int): String? {
        lowestNonHiddenAbility(pokemonId)?.let { return it }
        val defaultFormId = defaultFormIdBySpeciesId[speciesId] ?: return null
        if (defaultFormId == pokemonId) return null
        return lowestNonHiddenAbility(defaultFormId)
    }

    val species = pokemonRows.mapNotNull { pokemon ->
        val speciesRow = speciesById[pokemon.speciesId] ?: return@mapNotNull null
        val slots = typesByPokemonId[pokemon.id].orEmpty().sortedBy { it.slot }
        val type1 = slots.getOrNull(0)?.let { typeById[it.typeId] } ?: return@mapNotNull null
        val type2 = slots.getOrNull(1)?.let { typeById[it.typeId] }

        PokemonEntry(
            id = pokemon.id,
            name = pokemon.identifier,
            displayName = prettify(pokemon.identifier),
            speciesId = pokemon.speciesId,
            speciesName = speciesRow.identifier,
            types = type1 to type2,
            isLegendary = speciesRow.isLegendary,
            isMythical = speciesRow.isMythical,
            isFinalEvolution = pokemon.speciesId !in evolvesFromIds,
            generationIntroduced = speciesRow.generationId,
            defaultAbility = resolveDefaultAbility(pokemon.id, pokemon.speciesId),
        )
    }

    val moves = parseMoves(movesCsv).mapNotNull { move ->
        val type = typeById[move.typeId] ?: return@mapNotNull null
        val damageClass = when (move.damageClassId) {
            1 -> DamageClass.STATUS
            2 -> DamageClass.PHYSICAL
            3 -> DamageClass.SPECIAL
            else -> return@mapNotNull null
        }
        MoveEntry(
            id = move.id,
            name = move.identifier,
            displayName = prettify(move.identifier),
            type = type,
            power = move.power,
            damageClass = damageClass,
        )
    }

    val table = mutableMapOf<PokemonType, MutableMap<PokemonType, Double>>()
    for (row in parseTypeEfficacy(typeEfficacyCsv)) {
        val attacker = typeById[row.damageTypeId] ?: continue
        val defender = typeById[row.targetTypeId] ?: continue
        table.getOrPut(attacker) { mutableMapOf() }[defender] = row.damageFactor / 100.0
    }

    return ParsedDataset(species = species, moves = moves, abilities = abilities, typeChart = TypeChart(table))
}
