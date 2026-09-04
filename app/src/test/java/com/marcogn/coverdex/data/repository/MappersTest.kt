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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `PokemonEntry round-trips through its entity, dual-typed`() {
        val entry = PokemonEntry(
            id = 1,
            name = "bulbasaur",
            displayName = "Bulbasaur",
            speciesId = 1,
            speciesName = "bulbasaur",
            types = PokemonType.GRASS to PokemonType.POISON,
            isLegendary = false,
            isMythical = false,
            isFinalEvolution = false,
            generationIntroduced = 1,
            defaultAbility = "overgrow",
            isDefaultForm = true,
        )

        val roundTripped = entry.toEntity().toDomain()

        assertEquals(entry, roundTripped)
    }

    @Test
    fun `a single-typed entry's entity has a null type2, not an empty string`() {
        val entry = PokemonEntry(
            id = 25, name = "pikachu", displayName = "Pikachu", speciesId = 25, speciesName = "pikachu",
            types = PokemonType.ELECTRIC to null, isLegendary = false, isMythical = false,
            isFinalEvolution = true, generationIntroduced = 1, defaultAbility = "static", isDefaultForm = true,
        )

        val entity = entry.toEntity()

        assertNull(entity.type2)
        assertEquals(entry, entity.toDomain())
    }

    @Test
    fun `an entity with an unrecognized type string fails to map rather than crashing`() {
        val entity = PokeSpeciesEntity(
            id = 1, name = "x", displayName = "X", searchName = "x", speciesId = 1, speciesName = "x",
            type1 = "not-a-real-type", type2 = null, isLegendary = false, isMythical = false,
            isFinalEvolution = true, generationIntroduced = 1, defaultAbility = null, isDefaultForm = true,
        )

        assertNull(entity.toDomain())
    }

    @Test
    fun `MoveEntry round-trips, null power stays null`() {
        val move = MoveEntry(id = 1, name = "pound", displayName = "Pound", type = PokemonType.NORMAL, power = 40, damageClass = DamageClass.PHYSICAL)
        assertEquals(move, move.toEntity().toDomain())

        val statusMove = MoveEntry(id = 150, name = "splash", displayName = "Splash", type = PokemonType.NORMAL, power = null, damageClass = DamageClass.STATUS)
        val entity = statusMove.toEntity()
        assertNull(entity.power)
        assertEquals(statusMove, entity.toDomain())
    }

    @Test
    fun `an entity with an unrecognized damage class string fails to map rather than crashing`() {
        val entity = PokeMoveEntity(id = 1, name = "x", displayName = "X", searchName = "x", typeName = "normal", power = null, damageClass = "NOT_A_REAL_CLASS")

        assertNull(entity.toDomain())
    }

    @Test
    fun `AbilityEntry round-trips`() {
        val ability = AbilityEntry(id = 65, name = "overgrow", displayName = "Overgrow")
        assertEquals(ability, ability.toEntity().toDomain())
    }

    @Test
    fun `TypeChart round-trips through its entity list`() {
        val chart = TypeChart(
            mapOf(
                PokemonType.FIRE to mapOf(PokemonType.GRASS to 2.0, PokemonType.WATER to 0.5),
                PokemonType.WATER to mapOf(PokemonType.FIRE to 2.0),
            ),
        )

        val entities = chart.toEntities()
        assertEquals(3, entities.size)

        val rebuilt = entities.toTypeChart()
        assertEquals(2.0, rebuilt.multiplier(PokemonType.FIRE, PokemonType.GRASS), 0.0)
        assertEquals(0.5, rebuilt.multiplier(PokemonType.FIRE, PokemonType.WATER), 0.0)
        assertEquals(2.0, rebuilt.multiplier(PokemonType.WATER, PokemonType.FIRE), 0.0)
        assertEquals(1.0, rebuilt.multiplier(PokemonType.GRASS, PokemonType.GRASS), 0.0)
    }

    @Test
    fun `an efficacy entity with an unrecognized type string is skipped rather than crashing`() {
        val rebuilt = listOf(TypeEfficacyEntity(attacker = "not-a-type", defender = "fire", factor = 2.0)).toTypeChart()

        assertEquals(1.0, rebuilt.multiplier(PokemonType.NORMAL, PokemonType.FIRE), 0.0)
    }

    @Test
    fun `an ability entity maps with no failure path, unlike species and moves`() {
        val ability = PokeAbilityEntity(id = 1, name = "x", displayName = "X", searchName = "x")
        assertEquals(AbilityEntry(1, "x", "X"), ability.toDomain())
    }
}
