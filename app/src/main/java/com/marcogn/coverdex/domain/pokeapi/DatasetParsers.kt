package com.marcogn.coverdex.domain.pokeapi

/**
 * One pure row type and one pure parse function per pinned CSV file (see
 * docs/plan/reference-pokedata.md §2 for exact headers). Every value is looked up from
 * [CsvParser]'s name-keyed rows, never by position.
 */

data class PokemonCsvRow(val id: Int, val identifier: String, val speciesId: Int, val isDefault: Boolean)

fun parsePokemon(csv: String): List<PokemonCsvRow> =
    CsvParser.parse(csv).map { row ->
        PokemonCsvRow(
            id = row.getValue("id").toInt(),
            identifier = row.getValue("identifier"),
            speciesId = row.getValue("species_id").toInt(),
            isDefault = row.getValue("is_default") == "1",
        )
    }

data class SpeciesCsvRow(
    val id: Int,
    val identifier: String,
    val generationId: Int,
    val evolvesFromSpeciesId: Int?,
    val isLegendary: Boolean,
    val isMythical: Boolean,
)

fun parseSpecies(csv: String): List<SpeciesCsvRow> =
    CsvParser.parse(csv).map { row ->
        SpeciesCsvRow(
            id = row.getValue("id").toInt(),
            identifier = row.getValue("identifier"),
            generationId = row.getValue("generation_id").toInt(),
            evolvesFromSpeciesId = row.getValue("evolves_from_species_id").toIntOrNull(),
            isLegendary = row.getValue("is_legendary") == "1",
            isMythical = row.getValue("is_mythical") == "1",
        )
    }

data class PokemonTypeCsvRow(val pokemonId: Int, val typeId: Int, val slot: Int)

fun parsePokemonTypes(csv: String): List<PokemonTypeCsvRow> =
    CsvParser.parse(csv).map { row ->
        PokemonTypeCsvRow(
            pokemonId = row.getValue("pokemon_id").toInt(),
            typeId = row.getValue("type_id").toInt(),
            slot = row.getValue("slot").toInt(),
        )
    }

data class PokemonAbilityCsvRow(val pokemonId: Int, val abilityId: Int, val isHidden: Boolean, val slot: Int)

fun parsePokemonAbilities(csv: String): List<PokemonAbilityCsvRow> =
    CsvParser.parse(csv).map { row ->
        PokemonAbilityCsvRow(
            pokemonId = row.getValue("pokemon_id").toInt(),
            abilityId = row.getValue("ability_id").toInt(),
            isHidden = row.getValue("is_hidden") == "1",
            slot = row.getValue("slot").toInt(),
        )
    }

data class AbilityCsvRow(val id: Int, val identifier: String)

fun parseAbilities(csv: String): List<AbilityCsvRow> =
    CsvParser.parse(csv).map { row ->
        AbilityCsvRow(id = row.getValue("id").toInt(), identifier = row.getValue("identifier"))
    }

data class MoveCsvRow(val id: Int, val identifier: String, val typeId: Int, val power: Int?, val damageClassId: Int)

fun parseMoves(csv: String): List<MoveCsvRow> =
    CsvParser.parse(csv).map { row ->
        MoveCsvRow(
            id = row.getValue("id").toInt(),
            identifier = row.getValue("identifier"),
            typeId = row.getValue("type_id").toInt(),
            power = row.getValue("power").toIntOrNull(),
            damageClassId = row.getValue("damage_class_id").toInt(),
        )
    }

data class TypeCsvRow(val id: Int, val identifier: String)

fun parseTypes(csv: String): List<TypeCsvRow> =
    CsvParser.parse(csv).map { row ->
        TypeCsvRow(id = row.getValue("id").toInt(), identifier = row.getValue("identifier"))
    }

data class TypeEfficacyCsvRow(val damageTypeId: Int, val targetTypeId: Int, val damageFactor: Int)

fun parseTypeEfficacy(csv: String): List<TypeEfficacyCsvRow> =
    CsvParser.parse(csv).map { row ->
        TypeEfficacyCsvRow(
            damageTypeId = row.getValue("damage_type_id").toInt(),
            targetTypeId = row.getValue("target_type_id").toInt(),
            damageFactor = row.getValue("damage_factor").toInt(),
        )
    }
