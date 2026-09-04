package com.marcogn.coverdex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marcogn.coverdex.data.local.dao.BackupDao
import com.marcogn.coverdex.data.local.dao.CustomPokemonDao
import com.marcogn.coverdex.data.local.dao.PokedexDao
import com.marcogn.coverdex.data.local.dao.TeamDao
import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
import com.marcogn.coverdex.data.local.entity.PokeAbilityEntity
import com.marcogn.coverdex.data.local.entity.PokeCacheMetaEntity
import com.marcogn.coverdex.data.local.entity.PokeMoveEntity
import com.marcogn.coverdex.data.local.entity.PokeSpeciesEntity
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity
import com.marcogn.coverdex.data.local.entity.TypeEfficacyEntity

@Database(
    entities = [
        PokeSpeciesEntity::class,
        PokeMoveEntity::class,
        PokeAbilityEntity::class,
        TypeEfficacyEntity::class,
        PokeCacheMetaEntity::class,
        TeamEntity::class,
        TeamMemberEntity::class,
        TeamMemberMoveEntity::class,
        CustomPokemonEntity::class,
        CustomPokemonMoveEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CoverDexDatabase : RoomDatabase() {
    abstract fun pokedexDao(): PokedexDao
    abstract fun teamDao(): TeamDao
    abstract fun customPokemonDao(): CustomPokemonDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val DATABASE_NAME = "coverdex.db"
    }
}
