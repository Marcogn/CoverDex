package com.marcogn.coverdex.ui.team.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.coverdex.data.settings.ThemePreferences
import com.marcogn.coverdex.domain.coverage.analyseTeam
import com.marcogn.coverdex.domain.coverage.sharedWeaknessCounts
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import com.marcogn.coverdex.domain.suggestion.Suggestion
import com.marcogn.coverdex.domain.suggestion.SuggestionOptions
import com.marcogn.coverdex.domain.suggestion.computeSuggestions
import com.marcogn.coverdex.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `combine()`s the team flow, the type chart, the full catalogue, the custom roster and the local
 * toggles (showMoves, includeCustomsAnalysis, excludeLegendaries, the generation filter) into one
 * [AnalysisUiState] — `docs/plan/phase-3-analysis.md` §2 and, from Phase 4 on,
 * `phase-4-suggestions-and-generator.md` §4. The `showMoves` gate lives here, not in the engine:
 * with the toggle off, members reach [analyseTeam] with their moves stripped, exactly what
 * `TeamDetailPage.tsx`'s `analysisMembers` memo does, so the engine uniformly falls back to
 * type-based coverage.
 */
private data class CoreData(
    val team: Team?,
    val chart: TypeChart?,
    val pool: List<PokemonEntry>,
    val roster: List<TeamMember>,
    val showMoves: Boolean,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val teamRepository: TeamRepository,
    private val pokedexRepository: PokedexRepository,
    customPokemonRepository: CustomPokemonRepository,
    themePreferences: ThemePreferences,
) : ViewModel() {

    private val teamId: String = savedStateHandle.toRoute<Destination.TeamDetail>().teamId

    private val includeCustomsAnalysis = MutableStateFlow(false)
    private val excludeLegendaries = MutableStateFlow(false)
    private val generationFilter = MutableStateFlow<Int?>(null)

    private val core = combine(
        teamRepository.team(teamId),
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.typeChart() else null },
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.allSpecies() else emptyList() },
        customPokemonRepository.roster,
        themePreferences.showMoves,
    ) { team, chart, pool, roster, showMoves -> CoreData(team, chart, pool, roster, showMoves) }

    val uiState: StateFlow<AnalysisUiState> = combine(
        core,
        includeCustomsAnalysis,
        excludeLegendaries,
        generationFilter,
    ) { core, includeCustoms, excludeLegendariesValue, genFilter ->
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
        val suggestions = core.chart?.let { chart ->
            computeSuggestions(
                chart,
                members,
                core.pool,
                core.roster,
                SuggestionOptions(
                    includeCustoms = includeCustoms,
                    excludeLegendaries = excludeLegendariesValue,
                    generation = genFilter,
                ),
            )
        } ?: emptyList()

        AnalysisUiState(
            members = members,
            chart = core.chart,
            coverage = coverage,
            sharedWeaknesses = sharedWeaknesses,
            showMoves = core.showMoves,
            roster = core.roster,
            suggestions = suggestions,
            includeCustomsAnalysis = includeCustoms,
            excludeLegendaries = excludeLegendariesValue,
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

    fun setExcludeLegendaries(value: Boolean) {
        excludeLegendaries.value = value
    }

    fun setGenerationFilter(value: Int?) {
        generationFilter.value = value
    }

    /** Adds or swaps [suggestion] into the team — the first empty slot in addition mode, or
     * [Suggestion.replacesMemberId]'s slot in replacement mode. A no-op if neither slot can be
     * found (e.g. the team changed underneath a stale card). Pulls the candidate's default
     * ability from the catalogue for a real species, same as the slot editor does when picking
     * one (`SlotEditorScreen.kt`); a custom candidate keeps whatever ability it already carries. */
    fun applySuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            val team = teamRepository.team(teamId).first() ?: return@launch
            val slotIndex = when (suggestion.kind) {
                Suggestion.Kind.ADD -> team.members.indexOfFirst { it == null }
                Suggestion.Kind.REPLACE -> team.members.indexOfFirst { it?.id == suggestion.replacesMemberId }
            }
            if (slotIndex < 0) return@launch

            val defaultAbility = suggestion.candidate.pokedexId?.let { pokedexRepository.speciesById(it)?.defaultAbility }
            val newMember = suggestion.candidate.copy(
                id = UUID.randomUUID().toString(),
                ability = defaultAbility ?: suggestion.candidate.ability,
            )
            teamRepository.saveMember(teamId, slotIndex, newMember)
        }
    }
}
