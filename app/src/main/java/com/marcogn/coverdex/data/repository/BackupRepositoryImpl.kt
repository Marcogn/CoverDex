package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.dao.BackupDao
import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity
import com.marcogn.coverdex.domain.backup.BackupPayload
import com.marcogn.coverdex.domain.backup.toBackupDto
import com.marcogn.coverdex.domain.backup.toDomain
import com.marcogn.coverdex.domain.repository.BackupRepository
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * [CustomPokemonRepository.roster] already discards `CustomPokemonEntity.createdAtEpochMillis` at
 * the domain-model level (`TeamMember` has no such field — `CustomPokemonWithMoves.toDomain()`
 * never carries it over); the timestamp exists purely as `observeRoster()`'s `ORDER BY` key, never
 * shown to the user. A restore can't preserve a value it was never handed, so
 * [importPayload] instead assigns each roster entry a strictly increasing synthetic timestamp
 * from its position in [BackupPayload.customPokemon] (itself already in that same order, per
 * [exportPayload]'s `roster.first()`) — this reproduces the one observable effect the real
 * timestamp ever had (the roster's display order), which is the actual invariant `docs/plan/
 * phase-5-import-export-and-settings.md` §4's "timestamps preserved" rule protects.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val teamRepository: TeamRepository,
    private val customPokemonRepository: CustomPokemonRepository,
    private val backupDao: BackupDao,
) : BackupRepository {

    override suspend fun exportPayload(): BackupPayload {
        val teams = teamRepository.teams.first()
        val roster = customPokemonRepository.roster.first()
        return BackupPayload(
            exportedAtEpochMillis = System.currentTimeMillis(),
            teams = teams.map { it.toBackupDto() },
            customPokemon = roster.map { it.toBackupDto() },
        )
    }

    override suspend fun importPayload(payload: BackupPayload) {
        val teamEntities = mutableListOf<TeamEntity>()
        val memberEntities = mutableListOf<TeamMemberEntity>()
        val moveEntities = mutableListOf<TeamMemberMoveEntity>()

        payload.teams.forEachIndexed { position, teamDto ->
            val team = teamDto.toDomain()
            teamEntities += TeamEntity(id = team.id, name = team.name, createdAtEpochMillis = team.createdAtEpochMillis, position = position)
            team.members.forEachIndexed { slotIndex, member ->
                if (member != null) {
                    memberEntities += member.toEntity(team.id, slotIndex)
                    moveEntities += member.movesToEntities()
                }
            }
        }

        val restoreTimeBase = System.currentTimeMillis()
        val customEntities = mutableListOf<CustomPokemonEntity>()
        val customMoveEntities = mutableListOf<CustomPokemonMoveEntity>()
        payload.customPokemon.forEachIndexed { index, dto ->
            val member = dto.toDomain()
            customEntities += CustomPokemonEntity(
                id = member.id,
                name = member.speciesName,
                type1 = member.types.first.apiName,
                type2 = member.types.second?.apiName,
                ability = member.ability,
                createdAtEpochMillis = restoreTimeBase + index,
                item = member.item,
            )
            customMoveEntities += member.movesToCustomEntities()
        }

        backupDao.replaceAll(teamEntities, memberEntities, moveEntities, customEntities, customMoveEntities)
    }
}
