package com.marcogn.coverdex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marcogn.coverdex.data.local.dao.PokedexDao
import com.marcogn.coverdex.data.local.entity.PokeAbilityEntity
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.data.local.entity.PokeMoveEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity
import com.marcogn.coverdex.data.local.entity.TypeEfficacyEntity

@Database(
    entities = [
        PokeSpeciesEntity::class,
        PokeMoveEntity::class,
        PokeAbilityEntity::class,
        TypeEfficacyEntity::class,
        PokeCacheMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CoverDexDatabase : RoomDatabase() {
    abstract fun pokedexDao(): PokedexDao

    companion object {
        const val DATABASE_NAME = "coverdex.db"
    }
}
