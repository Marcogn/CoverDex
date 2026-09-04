package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonWithMoves
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberWithMoves
import com.marcogn.coverdex.data.local.entity.TeamWithMembers
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.model.TeamMember

private const val SLOT_COUNT = 6
private const val MOVE_COUNT = 4

/**
 * [type1]/[typeName] on team and roster entities are written exclusively by this app's own code
 * (always a [PokemonType.apiName] value), unlike [com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity]'s
 * fields, which come from parsed network data and can legitimately fail to resolve. A row here
 * should never fail to parse; falling back instead of dropping the row (as [PokedexRepositoryImpl]
 * does for the re-downloadable cache) protects data the user cannot get back.
 */
private fun parseType(apiName: String): PokemonType = PokemonType.fromApiName(apiName) ?: PokemonType.NORMAL

private fun parseDamageClass(name: String): DamageClass = runCatching { DamageClass.valueOf(name) }.getOrElse { DamageClass.PHYSICAL }

fun PokemonMove.toEntity(memberId: String, moveIndex: Int): TeamMemberMoveEntity = TeamMemberMoveEntity(
    id = id,
    memberId = memberId,
    moveIndex = moveIndex,
    name = name,
    typeName = type.apiName,
    power = power,
    damageClass = damageClass.name,
    isCustom = isCustom,
)

fun PokemonMove.toCustomEntity(customId: String, moveIndex: Int): CustomPokemonMoveEntity = CustomPokemonMoveEntity(
    id = id,
    customId = customId,
    moveIndex = moveIndex,
    name = name,
    typeName = type.apiName,
    power = power,
    damageClass = damageClass.name,
    isCustom = isCustom,
)

fun TeamMemberMoveEntity.toDomain(): PokemonMove = PokemonMove(
    id = id,
    name = name,
    type = parseType(typeName),
    power = power,
    damageClass = parseDamageClass(damageClass),
    isCustom = isCustom,
)

fun CustomPokemonMoveEntity.toDomain(): PokemonMove = PokemonMove(
    id = id,
    name = name,
    type = parseType(typeName),
    power = power,
    damageClass = parseDamageClass(damageClass),
    isCustom = isCustom,
)

private fun List<PokemonMove?>.toMoveSlots(): List<PokemonMove?> {
    val padded = this + List(MOVE_COUNT - size) { null }
    return padded.take(MOVE_COUNT)
}

fun TeamMember.toEntity(teamId: String, slotIndex: Int): TeamMemberEntity = TeamMemberEntity(
    id = id,
    teamId = teamId,
    slotIndex = slotIndex,
    pokedexId = pokedexId,
    speciesName = speciesName,
    type1 = types.first.apiName,
    type2 = types.second?.apiName,
    ability = ability,
    isCustomSaved = isCustomSaved,
)

fun TeamMember.movesToEntities(): List<TeamMemberMoveEntity> =
    moves.toMoveSlots().mapIndexedNotNull { index, move -> move?.toEntity(id, index) }

fun TeamMember.toCustomEntity(): CustomPokemonEntity = CustomPokemonEntity(
    id = id,
    name = speciesName,
    type1 = types.first.apiName,
    type2 = types.second?.apiName,
    ability = ability,
    createdAtEpochMillis = System.currentTimeMillis(),
)

fun TeamMember.movesToCustomEntities(): List<CustomPokemonMoveEntity> =
    moves.toMoveSlots().mapIndexedNotNull { index, move -> move?.toCustomEntity(id, index) }

fun TeamMemberWithMoves.toDomain(): TeamMember {
    val movesByIndex = moves.associateBy { it.moveIndex }
    return TeamMember(
        id = member.id,
        pokedexId = member.pokedexId,
        speciesName = member.speciesName,
        types = parseType(member.type1) to member.type2?.let { parseType(it) },
        ability = member.ability,
        moves = (0 until MOVE_COUNT).map { index -> movesByIndex[index]?.toDomain() },
        isCustomSaved = member.isCustomSaved,
    )
}

fun CustomPokemonWithMoves.toDomain(): TeamMember {
    val movesByIndex = moves.associateBy { it.moveIndex }
    return TeamMember(
        id = custom.id,
        pokedexId = null,
        speciesName = custom.name,
        types = parseType(custom.type1) to custom.type2?.let { parseType(it) },
        ability = custom.ability,
        moves = (0 until MOVE_COUNT).map { index -> movesByIndex[index]?.toDomain() },
        isCustomSaved = true,
    )
}

fun TeamWithMembers.toDomain(): Team {
    val membersBySlot = members.associateBy { it.member.slotIndex }
    return Team(
        id = team.id,
        name = team.name,
        members = (0 until SLOT_COUNT).map { slot -> membersBySlot[slot]?.toDomain() },
        createdAtEpochMillis = team.createdAtEpochMillis,
    )
}
