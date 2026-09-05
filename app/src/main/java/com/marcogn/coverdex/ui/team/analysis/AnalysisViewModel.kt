package com.marcogn.coverdex.ui.team.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.coverdex.data.settings.SettingsPreferences
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** `-mega`/`-gmax`/`-dynamax`/`-mega-x`/`-mega-y` — `TeamDetailPage.tsx`'s own regex against the
 * catalogue's form name, ported verbatim (`docs/plan/phase-5-import-export-and-settings.md` §3).
 * Applied to the suggestion pool only, never `PokedexRepository.searchSpecies`. */
private val MEGA_DYNAMAX_FORM_REGEX = Regex("-mega|-gmax|-dynamax|-mega-x|-mega-y")

/**
 * `combine()`s the team flow, the type chart, the full catalogue, the custom roster and the
 * persisted/local toggles (showMoves, includeMegaDynamax, excludeLegendaries,
 * includeCustomsAnalysis, the generation filter) into one [AnalysisUiState] —
 * `docs/plan/phase-3-analysis.md` §2, `phase-4-suggestions-and-generator.md` §4 and, from Phase 5
 * on, `phase-5-import-export-and-settings.md` §3. The `showMoves` gate lives here, not in the
 * engine: with the toggle off, members reach [analyseTeam] with their moves stripped, exactly
 * what `TeamDetailPage.tsx`'s `analysisMembers` memo does, so the engine uniformly falls back to
 * type-based coverage.
 */
private data class DatasetCore(val chart: TypeChart?, val pool: List<PokemonEntry>)

private data class CoreData(
    val team: Team?,
    val dataset: DatasetCore,
    val roster: List<TeamMember>,
    val showMoves: Boolean,
    val includeMegaDynamax: Boolean,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val teamRepository: TeamRepository,
    private val pokedexRepository: PokedexRepository,
    customPokemonRepository: CustomPokemonRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    private val teamId: String = savedStateHandle.toRoute<Destination.TeamDetail>().teamId

    private val generationFilter = MutableStateFlow<Int?>(null)

    private val datasetCore = combine(
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.typeChart() else null },
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.allSpecies() else emptyList() },
    ) { chart, pool -> DatasetCore(chart, pool) }

    private val core = combine(
        teamRepository.team(teamId),
        datasetCore,
        customPokemonRepository.roster,
        settingsPreferences.showMoves,
        settingsPreferences.includeMegaDynamax,
    ) { team, dataset, roster, showMoves, includeMegaDynamax -> CoreData(team, dataset, roster, showMoves, includeMegaDynamax) }

    val uiState: StateFlow<AnalysisUiState> = combine(
        core,
        settingsPreferences.includeCustomsAnalysis,
        settingsPreferences.excludeLegendaries,
        generationFilter,
    ) { core, includeCustoms, excludeLegendaries, genFilter ->
        val filled = core.team?.members?.filterNotNull() ?: emptyList()
        val members = if (core.showMoves) filled else filled.map { it.copy(moves = List(4) { null }) }
        val coverage = core.dataset.chart?.let { analyseTeam(it, members) }
        val sharedWeaknesses = core.dataset.chart?.let { chart ->
            sharedWeaknessCounts(chart, members)
                .filterValues { it >= 2 }
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
        } ?: emptyList()
        val suggestionPool = if (core.includeMegaDynamax) {
            core.dataset.pool
        } else {
            core.dataset.pool.filterNot { MEGA_DYNAMAX_FORM_REGEX.containsMatchIn(it.name) }
        }
        val suggestions = core.dataset.chart?.let { chart ->
            computeSuggestions(
                chart,
                members,
                suggestionPool,
                core.roster,
                SuggestionOptions(
                    includeCustoms = includeCustoms,
                    excludeLegendaries = excludeLegendaries,
                    generation = genFilter,
                ),
            )
        } ?: emptyList()

        AnalysisUiState(
            members = members,
            chart = core.dataset.chart,
            coverage = coverage,
            sharedWeaknesses = sharedWeaknesses,
            showMoves = core.showMoves,
            roster = core.roster,
            suggestions = suggestions,
            includeCustomsAnalysis = includeCustoms,
            generationFilter = genFilter,
        )
    }
        // analyseTeam/computeSuggestions/sharedWeaknessCounts are real work against a catalogue
        // in the high hundreds (docs/post-migration-review.md, finding 2) — flowOn moves the
        // whole combine() above, transform lambda included, off Dispatchers.Main.immediate
        // (what viewModelScope.launch inside stateIn would otherwise use) and onto a background
        // thread, recomputed on every team edit, toggle flip and filter change alike.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalysisUiState(),
        )

    fun setIncludeCustomsAnalysis(value: Boolean) {
        viewModelScope.launch { settingsPreferences.setIncludeCustomsAnalysis(value) }
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
