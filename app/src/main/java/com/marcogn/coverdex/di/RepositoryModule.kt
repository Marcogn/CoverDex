package com.marcogn.coverdex.di

import com.marcogn.coverdex.data.repository.PokedexRepositoryImpl
import com.marcogn.coverdex.domain.repository.PokedexRepository
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
}
