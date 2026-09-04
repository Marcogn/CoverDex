package com.marcogn.coverdex.ui.team.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.coverdex.data.settings.ThemePreferences
import com.marcogn.coverdex.domain.coverage.analyseTeam
import com.marcogn.coverdex.domain.coverage.sharedWeaknessCounts
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import com.marcogn.coverdex.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * `combine()`s the team flow, the type chart, the custom roster and the local toggles (showMoves,
 * includeCustomsAnalysis, the generation filter) into one [AnalysisUiState] —
 * `docs/plan/phase-3-analysis.md` §2. The `showMoves` gate lives here, not in the engine: with the
 * toggle off, members reach [analyseTeam] with their moves stripped, exactly what
 * `TeamDetailPage.tsx`'s `analysisMembers` memo does, so the engine uniformly falls back to
 * type-based coverage.
 */
private data class CoreData(
    val team: Team?,
    val chart: TypeChart?,
    val roster: List<TeamMember>,
    val showMoves: Boolean,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val teamRepository: TeamRepository,
    pokedexRepository: PokedexRepository,
    customPokemonRepository: CustomPokemonRepository,
    themePreferences: ThemePreferences,
) : ViewModel() {

    private val teamId: String = savedStateHandle.toRoute<Destination.TeamDetail>().teamId

    private val includeCustomsAnalysis = MutableStateFlow(false)
    private val generationFilter = MutableStateFlow("all")

    private val core = combine(
        teamRepository.team(teamId),
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.typeChart() else null },
        customPokemonRepository.roster,
        themePreferences.showMoves,
    ) { team, chart, roster, showMoves -> CoreData(team, chart, roster, showMoves) }

    val uiState: StateFlow<AnalysisUiState> = combine(
        core,
        includeCustomsAnalysis,
        generationFilter,
    ) { core, includeCustoms, genFilter ->
        val filled = core.team?.members?.filterNotNull() ?: emptyList()
        val members = if (core.showMoves) filled else filled.map { it.copy(moves = List(4) { null }) }
        val coverage = core.chart?.let { analyseTeam(it, members) }
        val sharedWeaknesses = core.chart?.let { chart ->
            sharedWeaknessCounts(chart, members)
                .filterValues { it >= 2 }
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
        } ?: emptyList()

        AnalysisUiState(
            members = members,
            chart = core.chart,
            coverage = coverage,
            sharedWeaknesses = sharedWeaknesses,
            showMoves = core.showMoves,
            roster = core.roster,
            includeCustomsAnalysis = includeCustoms,
            generationFilter = genFilter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalysisUiState(),
    )

    fun setIncludeCustomsAnalysis(value: Boolean) {
        includeCustomsAnalysis.value = value
    }

    fun setGenerationFilter(value: String) {
        generationFilter.value = value
    }
}
