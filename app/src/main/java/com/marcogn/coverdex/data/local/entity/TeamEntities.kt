package com.marcogn.coverdex.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * User data — teams, their six slots, and the custom roster. `pokedexId` carries no foreign key
 * into the cache: species/type/ability values on a slot are denormalized snapshots, so wiping
 * the cached catalogue must never alter or blank a saved team (docs/plan/native-spec.md,
 * "Storage"). An empty slot is a row that does not exist, not a placeholder row — `slotIndex`
 * keeps identity stable across edits.
 */
@Entity(tableName = "team")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val position: Int,
)

@Entity(
    tableName = "team_member",
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("teamId")],
)
data class TeamMemberEntity(
    @PrimaryKey val id: String,
    val teamId: String,
    val slotIndex: Int,
    val pokedexId: Int?,
    val speciesName: String,
    val type1: String,
    val type2: String?,
    val ability: String?,
    val isCustomSaved: Boolean,
)

@Entity(
    tableName = "team_member_move",
    foreignKeys = [
        ForeignKey(
            entity = TeamMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memberId")],
)
data class TeamMemberMoveEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val moveIndex: Int,
    val name: String,
    val typeName: String,
    val power: Int?,
    val damageClass: String,
    val isCustom: Boolean,
)

@Entity(tableName = "custom_pokemon")
data class CustomPokemonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type1: String,
    val type2: String?,
    val ability: String?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "custom_pokemon_move",
    foreignKeys = [
        ForeignKey(
            entity = CustomPokemonEntity::class,
            parentColumns = ["id"],
            childColumns = ["customId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("customId")],
)
data class CustomPokemonMoveEntity(
    @PrimaryKey val id: String,
    val customId: String,
    val moveIndex: Int,
    val name: String,
    val typeName: String,
    val power: Int?,
    val damageClass: String,
    val isCustom: Boolean,
)

data class TeamMemberWithMoves(
    @Embedded val member: TeamMemberEntity,
    @Relation(parentColumn = "id", entityColumn = "memberId")
    val moves: List<TeamMemberMoveEntity>,
)

data class TeamWithMembers(
    @Embedded val team: TeamEntity,
    @Relation(entity = TeamMemberEntity::class, parentColumn = "id", entityColumn = "teamId")
    val members: List<TeamMemberWithMoves>,
)

data class CustomPokemonWithMoves(
    @Embedded val custom: CustomPokemonEntity,
    @Relation(parentColumn = "id", entityColumn = "customId")
    val moves: List<CustomPokemonMoveEntity>,
)
