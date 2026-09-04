package com.marcogn.coverdex.ui.team.analysis

import com.marcogn.coverdex.domain.coverage.TeamCoverage
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.suggestion.Suggestion

data class AnalysisUiState(
    /** The team's filled slots, with moves cleared when [showMoves] is off — `analyseTeam` and
     * every grid see exactly what `TeamDetailPage.tsx`'s `analysisMembers` memo produces. */
    val members: List<TeamMember> = emptyList(),
    val chart: TypeChart? = null,
    val coverage: TeamCoverage? = null,
    /** Shared-weakness types with count >= 2, sorted by count descending — matches
     * `CoverageGrid.tsx`'s `sortedWeaknesses`. */
    val sharedWeaknesses: List<Pair<PokemonType, Int>> = emptyList(),
    val showMoves: Boolean = false,
    val roster: List<TeamMember> = emptyList(),
    /** Every ranked suggestion from `computeSuggestions` — the screen displays only the first
     * five (`phase-4-suggestions-and-generator.md` §4), kept unsliced here so a future caller
     * (e.g. a "show more" affordance) is not blocked on a state-shape change. */
    val suggestions: List<Suggestion> = emptyList(),
    /** Backed by `SettingsPreferences.includeCustomsAnalysis` (Phase 5) — persisted, but still
     * toggled right from this screen's `SuggestionFilters`, same as `showMoves`. */
    val includeCustomsAnalysis: Boolean = false,
    /** `null` means "all generations" — a real generation number (1-9) otherwise. See
     * `domain/suggestion/SuggestionEngine.kt`'s doc comment for why this is a number, not the
     * TypeScript's id-range key. */
    val generationFilter: Int? = null,
) {
    val canAnalyse: Boolean get() = members.isNotEmpty()
}
