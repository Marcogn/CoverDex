package com.marcogn.coverdex.data.debug

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds sample data behind `BuildConfig.SEED_DEBUG_DATA` (debug builds only) — never in a release
 * build, never surfaced as real user data. Does nothing yet: there are no teams or custom Pokémon
 * to seed until Phase 2 (docs/plan/phase-2-teams-and-roster.md) adds those tables. The seam exists
 * now so that phase only has to fill in [seed], not wire it up.
 */
@Singleton
class DebugSeeder @Inject constructor() {
    suspend fun seed() {
        // No-op until Phase 2.
    }
}
