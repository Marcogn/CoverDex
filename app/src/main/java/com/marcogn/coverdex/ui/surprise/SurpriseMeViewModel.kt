package com.marcogn.coverdex.ui.surprise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.domain.generator.DEFAULT_CONSTRAINTS
import com.marcogn.coverdex.domain.generator.GeneratorConstraints
import com.marcogn.coverdex.domain.generator.generateTeam
import com.marcogn.coverdex.domain.generator.regenerateSlot
import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `legacy-web/src/components/SurpriseMe/SurpriseMeModal.tsx` is the behavioural reference, but
 * this is a full screen rather than a modal (`phase-4-suggestions-and-generator.md` §4: "design it
 * to match the rest of this app's Material 3 screens" — every other destination here is a screen,
 * not a dialog). Its three logical steps (lock anchors, set constraints, review the result) are
 * one scrollable screen instead of a wizard.
 */
@HiltViewModel
class SurpriseMeViewModel @Inject constructor(
    private val pokedexRepository: PokedexRepository,
    customPokemonRepository: CustomPokemonRepository,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    private val lockedMembers = MutableStateFlow<List<TeamMember>>(emptyList())
    private val constraints = MutableStateFlow(DEFAULT_CONSTRAINTS)
    private val result = MutableStateFlow<List<TeamMember>>(emptyList())
    private val warning = MutableStateFlow<String?>(null)
    private val isGenerating = MutableStateFlow(false)

    private data class Core(val chart: TypeChart?, val pool: List<PokemonEntry>, val customs: List<TeamMember>)

    /** [result], [warning] and [isGenerating] grouped so the final `combine()` below stays within
     * the stdlib's typed 5-flow overload — same reason `AnalysisViewModel` groups its own flows
     * into an intermediate data class rather than reaching for the untyped vararg overload. */
    private data class GenerationState(val result: List<TeamMember>, val warning: String?, val isGenerating: Boolean)

    private val core: Flow<Core> = combine(
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.typeChart() else null },
        pokedexRepository.cacheStatus.map { status -> if (status.isUsable) pokedexRepository.allSpecies() else emptyList() },
        customPokemonRepository.roster,
    ) { chart, pool, roster -> Core(chart, pool, roster) }

    private val generationState: Flow<GenerationState> = combine(result, warning, isGenerating) { res, warn, generating ->
        GenerationState(res, warn, generating)
    }

    val uiState: StateFlow<SurpriseMeUiState> = combine(
        core,
        lockedMembers,
        constraints,
        generationState,
    ) { core, locked, cons, gen ->
        SurpriseMeUiState(
            chart = core.chart,
            pool = core.pool,
            customs = core.customs,
            lockedMembers = locked,
            constraints = cons,
            result = gen.result,
            warning = gen.warning,
            isGenerating = gen.isGenerating,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SurpriseMeUiState(),
    )

    fun searchSpecies(query: String): Flow<List<PokemonEntry>> = pokedexRepository.searchSpecies(query)

    fun addLocked(entry: PokemonEntry) {
        if (lockedMembers.value.size >= 6) return
        lockedMembers.value = lockedMembers.value + TeamMember(
            id = "locked-${entry.id}",
            pokedexId = entry.id,
            speciesName = entry.displayName,
            types = entry.types,
            ability = entry.defaultAbility,
            moves = List(4) { null },
            isCustomSaved = false,
        )
    }

    fun removeLocked(index: Int) {
        lockedMembers.value = lockedMembers.value.filterIndexed { i, _ -> i != index }
    }

    fun updateConstraints(transform: (GeneratorConstraints) -> GeneratorConstraints) {
        constraints.value = transform(constraints.value)
    }

    /** `generateTeam`/`regenerateSlot` (below) score every eligible candidate against the whole
     * team on every step — real work against a catalogue in the high hundreds, not the handful of
     * entries any unit test's pool has. Launched directly on [Dispatchers.Default] (not via
     * `withContext` from a `Dispatchers.Main` coroutine) so the whole computation, including the
     * final [result]/[warning]/[isGenerating] writes, runs on a real background thread with no
     * hop back through the main dispatcher — see `docs/post-migration-review.md`, finding 2.
     * [isGenerating] itself flips to `true` synchronously, before the coroutine is even launched,
     * so a caller — a Compose click handler, or a test's `uiState.first { !it.isGenerating }` —
     * always observes the state change immediately rather than racing it. */
    fun generate() {
        if (isGenerating.value) return
        val chart = uiState.value.chart ?: return
        isGenerating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val res = generateTeam(chart, uiState.value.pool, lockedMembers.value, constraints.value, customs = uiState.value.customs)
            result.value = res.team
            warning.value = res.warning
            isGenerating.value = false
        }
    }

    fun regenerateAll() = generate()

    fun regenerateSlot(index: Int) {
        if (isGenerating.value) return
        val chart = uiState.value.chart ?: return
        val current = result.value
        if (index !in current.indices) return
        isGenerating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val newMember = regenerateSlot(chart, uiState.value.pool, current, index, constraints.value, customs = uiState.value.customs)
            result.value = current.toMutableList().also { it[index] = newMember }
            isGenerating.value = false
        }
    }

    /** Creates a brand-new team named [teamName] and writes the generated result into its six
     * slots, then hands the new id to [onCreated] so the caller can navigate to it. */
    fun keep(teamName: String, onCreated: (String) -> Unit) {
        val team = result.value
        if (team.isEmpty()) return
        viewModelScope.launch {
            val teamId = teamRepository.createTeam(teamName)
            team.forEachIndexed { index, member ->
                teamRepository.saveMember(teamId, index, member.copy(id = UUID.randomUUID().toString()))
            }
            onCreated(teamId)
        }
    }
}
