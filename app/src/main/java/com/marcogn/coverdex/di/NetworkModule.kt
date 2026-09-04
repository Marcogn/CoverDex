package com.marcogn.coverdex.di

import com.marcogn.coverdex.data.pokeapi.DatasetSource
import com.marcogn.coverdex.data.pokeapi.PokeDataClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindDatasetSource(impl: PokeDataClient): DatasetSource
}
