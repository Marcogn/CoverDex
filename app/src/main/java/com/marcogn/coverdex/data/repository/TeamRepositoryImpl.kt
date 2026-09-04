package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.dao.TeamDao
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.repository.TeamRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TeamRepositoryImpl @Inject constructor(
    private val teamDao: TeamDao,
) : TeamRepository {

    override val teams: Flow<List<Team>> = teamDao.observeTeams().map { rows -> rows.map { it.toDomain() } }

    override fun team(id: String): Flow<Team?> = teamDao.observeTeam(id).map { it?.toDomain() }

    override suspend fun createTeam(name: String): String {
        val id = UUID.randomUUID().toString()
        teamDao.insertTeam(
            TeamEntity(
                id = id,
                name = name,
                createdAtEpochMillis = System.currentTimeMillis(),
                position = teamDao.countTeams(),
            ),
        )
        return id
    }

    override suspend fun renameTeam(id: String, name: String) = teamDao.renameTeam(id, name)

    override suspend fun deleteTeam(id: String) = teamDao.deleteTeam(id)

    override suspend fun saveMember(teamId: String, slotIndex: Int, member: TeamMember) =
        teamDao.saveMember(teamId, slotIndex, member.toEntity(teamId, slotIndex), member.movesToEntities())

    override suspend fun clearSlot(teamId: String, slotIndex: Int) = teamDao.clearSlot(teamId, slotIndex)
}
