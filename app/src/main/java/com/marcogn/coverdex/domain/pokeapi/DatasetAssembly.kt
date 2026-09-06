package com.marcogn.coverdex.domain.pokeapi

import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PastBst
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.SpeciesAbility
import com.marcogn.coverdex.domain.model.TypeChart

/** `"mr-mime"` -> `"Mr Mime"`. The fallback for a species/form display name (never corrected in
 * Phase 7 — see docs/plan/phase-7-accuracy-and-customization.md, "Explicitly out of scope") and
 * for any ability/move identifier absent from the pinned `ability_names.csv`/`move_names.csv`. */
fun prettify(kebabName: String): String =
    kebabName.split("-").joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

/** `"mr-mime"` -> `"mrmime"`, so "mrmime"/"mr mime"/"mr-mime" all match the same search row. */
fun searchKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

private const val STAT_HP = 1
private const val STAT_ATTACK = 2
private const val STAT_DEFENSE = 3
private const val STAT_SPECIAL_ATTACK = 4
private const val STAT_SPECIAL_DEFENSE = 5
private const val STAT_SPEED = 6
private const val STAT_SPECIAL_GEN1 = 9
private val CURRENT_GEN_STAT_IDS = listOf(STAT_HP, STAT_ATTACK, STAT_DEFENSE, STAT_SPECIAL_ATTACK, STAT_SPECIAL_DEFENSE, STAT_SPEED)

/** The assembled catalogue — what [com.marcogn.coverdex.data.pokeapi.DatasetSyncManager] writes
 * to the cache tables in one transaction. [pastBst] and [pokemonAbilities] were added in Phase 7
 * (docs/plan/phase-7-accuracy-and-customization.md §2.3). */
data class ParsedDataset(
    val species: List<PokemonEntry>,
    val moves: List<MoveEntry>,
    val abilities: List<AbilityEntry>,
    val typeChart: TypeChart,
    val pastBst: List<PastBst>,
    val pokemonAbilities: List<SpeciesAbility>,
)

/**
 * Joins the 12 pinned CSVs into [ParsedDataset], implementing docs/plan/reference-pokedata.md §3
 * and docs/plan/phase-7-accuracy-and-customization.md §2 exactly. Pure: no Android imports, no
 * I/O — the caller ([data.pokeapi.DatasetSyncManager]) fetches the raw CSV text and hands it here.
 */
@Suppress("LongParameterList")
fun assembleDataset(
    pokemonCsv: String,
    speciesCsv: String,
    pokemonTypesCsv: String,
    pokemonAbilitiesCsv: String,
    abilitiesCsv: String,
    movesCsv: String,
    typesCsv: String,
    typeEfficacyCsv: String,
    pokemonStatsCsv: String,
    pokemonStatsPastCsv: String,
    abilityNamesCsv: String,
    moveNamesCsv: String,
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

    // English display names, falling back to prettify() when a form/move/ability has no
    // ability_names.csv/move_names.csv row (phase-7-accuracy-and-customization.md §2.1/§0.3).
    val abilityNameById: Map<Int, String> = parseAbilityNames(abilityNamesCsv).associate { it.id to it.name }
    val moveNameById: Map<Int, String> = parseMoveNames(moveNamesCsv).associate { it.id to it.name }

    val abilityRows = parseAbilities(abilitiesCsv)
    val abilityIdentifierById = abilityRows.associate { it.id to it.identifier }
    val abilityDisplayNameById: Map<Int, String> =
        abilityRows.associate { it.id to (abilityNameById[it.id] ?: prettify(it.identifier)) }
    val abilities = abilityRows.map {
        AbilityEntry(id = it.id, name = it.identifier, displayName = abilityDisplayNameById.getValue(it.id))
    }

    val pokemonRows = parsePokemon(pokemonCsv)
    val typesByPokemonId = parsePokemonTypes(pokemonTypesCsv).groupBy { it.pokemonId }
    val pokemonAbilityRows = parsePokemonAbilities(pokemonAbilitiesCsv)
    val abilitiesByPokemonId = pokemonAbilityRows.groupBy { it.pokemonId }
    val defaultFormIdBySpeciesId = pokemonRows.filter { it.isDefault }.associate { it.speciesId to it.id }

    fun lowestNonHiddenAbility(pokemonId: Int): String? {
        val row = abilitiesByPokemonId[pokemonId].orEmpty()
            .filter { !it.isHidden }
            .minByOrNull { it.slot }
            ?: return null
        return abilityDisplayNameById[row.abilityId]
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

    // --- Base stat totals (phase-7-accuracy-and-customization.md §2.2) ---

    val currentStatsByPokemon: Map<Int, Map<Int, Int>> =
        parsePokemonStats(pokemonStatsCsv).groupBy { it.pokemonId }
            .mapValues { (_, rows) -> rows.associate { it.statId to it.baseStat } }

    // (pokemonId, statId) -> the past rows for that stat, each "held through generationId".
    val pastStatRows = parsePokemonStatsPast(pokemonStatsPastCsv)
    val pastByPokemonStat: Map<Pair<Int, Int>, List<PokemonStatPastCsvRow>> =
        pastStatRows.groupBy { it.pokemonId to it.statId }

    fun currentStat(pokemonId: Int, statId: Int): Int = currentStatsByPokemon[pokemonId]?.get(statId) ?: 0

    fun statAt(pokemonId: Int, statId: Int, generation: Int): Int {
        val applicable = pastByPokemonStat[pokemonId to statId].orEmpty().filter { it.generationId >= generation }
        val chosen = applicable.minByOrNull { it.generationId }
        return chosen?.baseStat ?: currentStat(pokemonId, statId)
    }

    fun bstAt(pokemonId: Int, generation: Int): Int =
        if (generation <= 1) {
            // Gen I has no Special Attack/Special Defense split — the canonical total is the sum
            // of FIVE stats (HP/Attack/Defense/Speed/Special), never six. A Gen-I total is on a
            // different scale from every later generation's and must never be compared to one.
            statAt(pokemonId, STAT_HP, generation) +
                statAt(pokemonId, STAT_ATTACK, generation) +
                statAt(pokemonId, STAT_DEFENSE, generation) +
                statAt(pokemonId, STAT_SPEED, generation) +
                statAt(pokemonId, STAT_SPECIAL_GEN1, generation)
        } else {
            CURRENT_GEN_STAT_IDS.sumOf { statId -> statAt(pokemonId, statId, generation) }
        }

    fun currentBst(pokemonId: Int): Int = CURRENT_GEN_STAT_IDS.sumOf { statId -> currentStat(pokemonId, statId) }

    val pastBst: List<PastBst> = pastStatRows.map { it.pokemonId }.distinct().flatMap { pokemonId ->
        val current = currentBst(pokemonId)
        val generations = pastByPokemonStat.keys.filter { it.first == pokemonId }
            .flatMap { key -> pastByPokemonStat.getValue(key).map { it.generationId } }
            .distinct()
        generations.mapNotNull { generation ->
            val bst = bstAt(pokemonId, generation)
            if (bst != current || generation == 1) PastBst(pokemonId, generation, bst) else null
        }
    }

    val pokemonAbilities: List<SpeciesAbility> = pokemonAbilityRows.map { row ->
        SpeciesAbility(
            pokemonId = row.pokemonId,
            slug = abilityIdentifierById[row.abilityId] ?: "",
            displayName = abilityDisplayNameById[row.abilityId] ?: "",
            isHidden = row.isHidden,
            slot = row.slot,
        )
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
            isDefaultForm = pokemon.isDefault,
            baseStatTotal = currentBst(pokemon.id),
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
            displayName = moveNameById[move.id] ?: prettify(move.identifier),
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

    return ParsedDataset(
        species = species,
        moves = moves,
        abilities = abilities,
        typeChart = TypeChart(table),
        pastBst = pastBst,
        pokemonAbilities = pokemonAbilities,
    )
}
