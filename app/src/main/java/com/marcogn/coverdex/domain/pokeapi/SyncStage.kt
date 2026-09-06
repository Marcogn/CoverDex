package com.marcogn.coverdex.domain.pokeapi

/**
 * Bumped whenever a cache table's shape changes. Compared against the stored
 * `poke_cache_meta.schemaVersion` row; a mismatch is treated as "absent" and silently re-synced —
 * never a crash on a stale row. See docs/plan/reference-pokedata.md §6.
 *
 * Bumped to 2 in Phase 7: `poke_species` gained `baseStatTotal` and two new cache tables
 * (`poke_pokemon_ability`, `poke_species_bst_past`) were added. Without this bump every existing
 * install would keep reporting its Room-v2-era cache as fresh and never download base stats or
 * per-form abilities — see docs/plan/phase-7-accuracy-and-customization.md §8.
 */
const val DATASET_SCHEMA_VERSION = 2

/**
 * The coarse phase of a dataset sync run — unlike Hall of Memories' PokéAPI sync, this one has no
 * independently-fresh sub-stages: all 8 CSVs are fetched together and written in a single
 * transaction (see docs/plan/reference-pokedata.md, "a sync either commits fully ... or writes
 * nothing"), so there is one stage per phase of that single run, not one per resource.
 */
enum class SyncStage {
    DOWNLOADING,
    PARSING,
    WRITING,
}
