package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonWithMoves
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberWithMoves
import com.marcogn.coverdex.data.local.entity.TeamWithMembers
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamMappersTest {

    private fun move(id: String, index: Int, name: String = "tackle") =
        PokemonMove(id = id, name = name, type = PokemonType.NORMAL, power = 40, damageClass = DamageClass.PHYSICAL, isCustom = false)

    private fun member(moves: List<PokemonMove?> = listOf(move("mv0", 0), null, null, null)) = TeamMember(
        id = "m1",
        pokedexId = 1,
        speciesName = "bulbasaur",
        types = PokemonType.GRASS to PokemonType.POISON,
        ability = "overgrow",
        moves = moves,
        isCustomSaved = false,
    )

    @Test
    fun `a TeamMember round-trips through its entity and move entities, dual-typed with a gap in its moves`() {
        val original = member(moves = listOf(move("mv0", 0), null, move("mv2", 2, "vine-whip"), null))

        val entity = original.toEntity(teamId = "t1", slotIndex = 0)
        val moveEntities = original.movesToEntities()
        val rebuilt = TeamMemberWithMoves(entity, moveEntities).toDomain()

        assertEquals(original, rebuilt)
    }

    @Test
    fun `item round-trips through the team member entity`() {
        val original = member().copy(item = "Air Balloon")

        val entity = original.toEntity(teamId = "t1", slotIndex = 0)
        assertEquals("Air Balloon", entity.item)

        val rebuilt = TeamMemberWithMoves(entity, original.movesToEntities()).toDomain()
        assertEquals(original, rebuilt)
    }

    @Test
    fun `a single-typed member's entity has a null type2`() {
        val original = member().copy(types = PokemonType.FIRE to null)

        val entity = original.toEntity(teamId = "t1", slotIndex = 0)

        assertNull(entity.type2)
    }

    @Test
    fun `movesToEntities never writes more than 4 moves, padding or truncating to the fixed 4 slots`() {
        val tooFew = member(moves = listOf(move("mv0", 0)))
        assertEquals(1, tooFew.movesToEntities().size)

        val exactlyFour = member(moves = listOf(move("mv0", 0), move("mv1", 1), move("mv2", 2), move("mv3", 3)))
        assertEquals(4, exactlyFour.movesToEntities().size)
    }

    @Test
    fun `an unrecognized type or damage class string falls back rather than dropping the row`() {
        // Unlike PokedexRepositoryImpl's cache mappers (a re-downloadable cache can afford to skip
        // a bad row), team data is irreplaceable — see TeamMappers.kt's parseType/parseDamageClass.
        val entity = TeamMemberEntity(
            id = "m1", teamId = "t1", slotIndex = 0, pokedexId = null, speciesName = "mystery",
            type1 = "not-a-real-type", type2 = null, ability = null, isCustomSaved = false,
        )
        val moveEntity = TeamMemberMoveEntity(
            id = "mv0", memberId = "m1", moveIndex = 0, name = "mystery-move",
            typeName = "not-a-real-type", power = null, damageClass = "NOT_A_REAL_CLASS", isCustom = true,
        )

        val domain = TeamMemberWithMoves(entity, listOf(moveEntity)).toDomain()

        assertEquals(PokemonType.NORMAL, domain.types.first)
        assertEquals(PokemonType.NORMAL, domain.moves[0]?.type)
        assertEquals(DamageClass.PHYSICAL, domain.moves[0]?.damageClass)
    }

    @Test
    fun `a Team round-trips with an empty slot represented as null, not a placeholder`() {
        val team = com.marcogn.coverdex.domain.model.Team(
            id = "t1",
            name = "My Team",
            members = listOf(member(), null, null, null, null, null),
            createdAtEpochMillis = 1000L,
        )
        val entity = TeamEntity(id = team.id, name = team.name, createdAtEpochMillis = team.createdAtEpochMillis, position = 0)
        val memberEntity = team.members[0]!!.toEntity(teamId = "t1", slotIndex = 0)
        val moveEntities = team.members[0]!!.movesToEntities()

        val rebuilt = TeamWithMembers(entity, listOf(TeamMemberWithMoves(memberEntity, moveEntities))).toDomain()

        assertEquals(6, rebuilt.members.size)
        assertEquals(team.members[0], rebuilt.members[0])
        assertTrue(rebuilt.members.drop(1).all { it == null })
    }

    @Test
    fun `a custom roster entry maps with pokedexId null and isCustomSaved true always`() {
        val entity = CustomPokemonEntity(id = "c1", name = "Custom Bulba", type1 = "grass", type2 = "poison", ability = "overgrow", createdAtEpochMillis = 1000L)
        val moveEntity = CustomPokemonMoveEntity(id = "mv0", customId = "c1", moveIndex = 0, name = "tackle", typeName = "normal", power = 40, damageClass = "PHYSICAL", isCustom = false)

        val domain = CustomPokemonWithMoves(entity, listOf(moveEntity)).toDomain()

        assertNull(domain.pokedexId)
        assertTrue(domain.isCustomSaved)
        assertEquals("Custom Bulba", domain.speciesName)
        assertEquals(1, domain.moves.filterNotNull().size)
    }

    @Test
    fun `a TeamMember maps back to a custom roster entity and move entities`() {
        val original = member()

        val entity = original.toCustomEntity()
        val moveEntities = original.movesToCustomEntities()

        assertEquals(original.speciesName, entity.name)
        assertEquals(1, moveEntities.size)
        assertEquals(original.moves[0]?.name, moveEntities.first().name)
    }

    @Test
    fun `item round-trips through the custom roster entity`() {
        val original = member().copy(item = "Chilan Berry")

        val entity = original.toCustomEntity()
        assertEquals("Chilan Berry", entity.item)

        val rebuilt = CustomPokemonWithMoves(entity, original.movesToCustomEntities()).toDomain()
        assertEquals("Chilan Berry", rebuilt.item)
    }
}
