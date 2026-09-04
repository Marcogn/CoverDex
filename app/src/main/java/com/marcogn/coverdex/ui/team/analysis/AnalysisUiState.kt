package com.marcogn.coverdex.ui.team.analysis

import com.marcogn.coverdex.domain.coverage.TeamCoverage
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart

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
    /** Unused by anything in this phase — the Suggestions section is a placeholder until Phase 4
     * wires the actual computation — but combined into state now per
     * `phase-3-analysis.md`'s own description of `AnalysisViewModel`, so Phase 4 only adds a
     * computation, not a state-shape change. */
    val includeCustomsAnalysis: Boolean = false,
    val generationFilter: String = "all",
) {
    val canAnalyse: Boolean get() = members.isNotEmpty()
}
