package com.marcogn.coverdex.di

import android.content.Context
import androidx.room.Room
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.local.dao.PokedexDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // No fallbackToDestructiveMigration(), ever — from Phase 2 onward the app holds data that
    // cannot be re-created. Schema v1 is the first version; a future schema change adds a
    // numbered MIGRATION_x_y here.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoverDexDatabase =
        Room.databaseBuilder(context, CoverDexDatabase::class.java, CoverDexDatabase.DATABASE_NAME).build()

    @Provides
    fun providePokedexDao(database: CoverDexDatabase): PokedexDao = database.pokedexDao()
}
