package com.marcogn.coverdex.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only — fallbackToDestructiveMigration() is banned outright (CLAUDE.md). Adds the
 * user-data tables from docs/plan/phase-2-teams-and-roster.md §1; the five Phase 1 cache tables
 * are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `team` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `team_member` (`id` TEXT NOT NULL, `teamId` TEXT NOT NULL, " +
                "`slotIndex` INTEGER NOT NULL, `pokedexId` INTEGER, `speciesName` TEXT NOT NULL, " +
                "`type1` TEXT NOT NULL, `type2` TEXT, `ability` TEXT, `isCustomSaved` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`teamId`) REFERENCES `team`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_team_member_teamId` ON `team_member` (`teamId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `team_member_move` (`id` TEXT NOT NULL, `memberId` TEXT NOT NULL, " +
                "`moveIndex` INTEGER NOT NULL, `name` TEXT NOT NULL, `typeName` TEXT NOT NULL, " +
                "`power` INTEGER, `damageClass` TEXT NOT NULL, `isCustom` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`memberId`) REFERENCES `team_member`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_team_member_move_memberId` ON `team_member_move` (`memberId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_pokemon` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`type1` TEXT NOT NULL, `type2` TEXT, `ability` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_pokemon_move` (`id` TEXT NOT NULL, `customId` TEXT NOT NULL, " +
                "`moveIndex` INTEGER NOT NULL, `name` TEXT NOT NULL, `typeName` TEXT NOT NULL, " +
                "`power` INTEGER, `damageClass` TEXT NOT NULL, `isCustom` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`customId`) REFERENCES `custom_pokemon`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_pokemon_move_customId` ON `custom_pokemon_move` (`customId`)")
    }
}
