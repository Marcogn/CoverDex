package com.marcogn.coverdex.di

import com.marcogn.coverdex.data.repository.CustomPokemonRepositoryImpl
import com.marcogn.coverdex.data.repository.PokedexRepositoryImpl
import com.marcogn.coverdex.data.repository.TeamRepositoryImpl
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPokedexRepository(impl: PokedexRepositoryImpl): PokedexRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(impl: TeamRepositoryImpl): TeamRepository

    @Binds
    @Singleton
    abstract fun bindCustomPokemonRepository(impl: CustomPokemonRepositoryImpl): CustomPokemonRepository
}
