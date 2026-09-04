package com.marcogn.coverdex.data.repository

import com.marcogn.coverdex.data.local.dao.CustomPokemonDao
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CustomPokemonRepositoryImpl @Inject constructor(
    private val customPokemonDao: CustomPokemonDao,
) : CustomPokemonRepository {

    override val roster: Flow<List<TeamMember>> = customPokemonDao.observeRoster().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(member: TeamMember) = customPokemonDao.upsert(member.toCustomEntity(), member.movesToCustomEntities())

    override suspend fun delete(id: String) = customPokemonDao.delete(id)
}
