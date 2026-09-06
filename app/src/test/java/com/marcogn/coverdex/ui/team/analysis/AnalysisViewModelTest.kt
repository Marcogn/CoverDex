package com.marcogn.coverdex.ui.team.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.MainDispatcherRule
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.repository.CustomPokemonRepositoryImpl
import com.marcogn.coverdex.data.repository.TeamRepositoryImpl
import com.marcogn.coverdex.data.settings.SettingsPreferences
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.mockPokemonList
import com.marcogn.coverdex.domain.mockTypeChart
import com.marcogn.coverdex.domain.suggestion.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `docs/plan/phase-3-analysis.md`'s "Tests" section: with `showMoves` off, a member holding
 * damaging moves still produces type-based coverage; with it on, move-based.
 */
// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class AnalysisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: CoverDexDatabase
    private lateinit var teamRepository: TeamRepositoryImpl
    private lateinit var customPokemonRepository: CustomPokemonRepositoryImpl
    private lateinit var settingsPreferences: SettingsPreferences
    private val chart = mockTypeChart()

    @Before
    fun createDependencies() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepositoryImpl(database.teamDao())
        customPokemonRepository = CustomPokemonRepositoryImpl(database.customPokemonDao())
        settingsPreferences = SettingsPreferences(context)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun viewModel(teamId: String, pool: List<PokemonEntry> = emptyList()) = AnalysisViewModel(
        savedStateHandle = SavedStateHandle(mapOf("teamId" to teamId)),
        teamRepository = teamRepository,
        pokedexRepository = FakePokedexRepository(chart, pool = pool),
        customPokemonRepository = customPokemonRepository,
        settingsPreferences = settingsPreferences,
    )

    private fun waterFlyingWithElectricMove(): TeamMember {
        val thunderbolt = PokemonMove(
            id = "mv1", name = "Thunderbolt", type = PokemonType.ELECTRIC,
            power = 90, damageClass = DamageClass.SPECIAL, isCustom = false,
        )
        return TeamMember(
            id = "m1", pokedexId = null, speciesName = "Gyarados",
            types = PokemonType.WATER to PokemonType.FLYING, ability = null,
            moves = listOf(thunderbolt, null, null, null), isCustomSaved = false,
        )
    }

    @Test
    fun `with showMoves off, a member holding damaging moves still produces type-based coverage`() = runTest(mainDispatcherRule.dispatcher) {
        settingsPreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T1")
        teamRepository.saveMember(teamId, 0, waterFlyingWithElectricMove())

        val state = viewModel(teamId).uiState.first { it.coverage != null }

        assertEquals(listOf(null, null, null, null), state.members.first().moves)
        // Water/Flying's own types cover Fire (Water 2x) but, unlike the stripped Electric move,
        // never Water itself (Electric->Water is 2x; Water->Water is only 0.5x).
        assertTrue(state.coverage!!.unionCovered.contains(PokemonType.FIRE))
        assertTrue(!state.coverage!!.unionCovered.contains(PokemonType.WATER))
    }

    @Test
    fun `with showMoves on, the same member produces move-based coverage`() = runTest(mainDispatcherRule.dispatcher) {
        settingsPreferences.setShowMoves(true)
        val teamId = teamRepository.createTeam("T2")
        teamRepository.saveMember(teamId, 0, waterFlyingWithElectricMove())

        val state = viewModel(teamId).uiState.first { it.coverage != null }

        assertEquals(1, state.members.first().moves.filterNotNull().size)
        // Move-based coverage uses only the Electric move (Electric->Water is 2x) — Water/Flying's
        // own types are ignored entirely, so Fire (which only the *types* would cover) is absent.
        assertTrue(state.coverage!!.unionCovered.contains(PokemonType.WATER))
        assertTrue(!state.coverage!!.unionCovered.contains(PokemonType.FIRE))
    }

    @Test
    fun `canAnalyse is false for an empty team and true once a slot is filled`() = runTest(mainDispatcherRule.dispatcher) {
        settingsPreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T3")

        val empty = viewModel(teamId).uiState.first { it.chart != null }
        assertEquals(false, empty.canAnalyse)

        teamRepository.saveMember(teamId, 0, waterFlyingWithElectricMove())
        val filled = viewModel(teamId).uiState.first { it.members.isNotEmpty() }
        assertTrue(filled.canAnalyse)
    }

    @Test
    fun `suggestions are computed from the catalogue and respect the generation filter`() = runTest(mainDispatcherRule.dispatcher) {
        settingsPreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T4")
        val pikachu = TeamMember(
            id = "m1", pokedexId = 25, speciesName = "Pikachu",
            types = PokemonType.ELECTRIC to null, ability = null,
            moves = List(4) { null }, isCustomSaved = false,
        )
        teamRepository.saveMember(teamId, 0, pikachu)

        val vm = viewModel(teamId, pool = mockPokemonList())
        val unfiltered = vm.uiState.first { it.suggestions.isNotEmpty() }
        assertTrue(unfiltered.suggestions.all { it.kind == Suggestion.Kind.ADD })

        vm.setGenerationFilter(4)
        val gen4 = vm.uiState.first { it.generationFilter == 4 }
        assertEquals(listOf("Garchomp"), gen4.suggestions.map { it.candidateLabel })
    }

    @Test
    fun `suggestionCount reflects SettingsPreferences and defaults to 5`() = runTest(mainDispatcherRule.dispatcher) {
        settingsPreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T-suggestion-count")

        val vm = viewModel(teamId, pool = mockPokemonList())
        val default = vm.uiState.first { it.chart != null }
        assertEquals(5, default.suggestionCount)

        settingsPreferences.setSuggestionCount(8)
        val updated = vm.uiState.first { it.suggestionCount == 8 }
        assertEquals(8, updated.suggestionCount)
    }

    @Test
    fun `coverage is computed without ever advancing the Main test dispatcher`() = runTest(mainDispatcherRule.dispatcher) {
        // mainDispatcherRule.dispatcher is a StandardTestDispatcher, which only runs work queued
        // on it when explicitly advanced (advanceUntilIdle()/runCurrent()) — this test never
        // calls either. Before finding 2's fix, analyseTeam ran inside stateIn's own collector,
        // i.e. on Dispatchers.Main.immediate, so this would hang until timing out; flowOn(Default)
        // moves it onto a real background thread instead, which completes independently.
        settingsPreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T-dispatcher")
        teamRepository.saveMember(teamId, 0, waterFlyingWithElectricMove())

        val state = viewModel(teamId).uiState.first { it.coverage != null }
        assertTrue(state.coverage!!.unionCovered.isNotEmpty())
    }

    @Test
    fun `applySuggestion in addition mode writes the candidate into the first empty slot`() = runTest(mainDispatcherRule.dispatcher) {
        settingsPreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T5")
        val pikachu = TeamMember(
            id = "m1", pokedexId = 25, speciesName = "Pikachu",
            types = PokemonType.ELECTRIC to null, ability = null,
            moves = List(4) { null }, isCustomSaved = false,
        )
        teamRepository.saveMember(teamId, 0, pikachu)

        val vm = viewModel(teamId, pool = mockPokemonList())
        val state = vm.uiState.first { it.suggestions.isNotEmpty() }
        val top = state.suggestions.first()

        vm.applySuggestion(top)

        val updatedTeam = teamRepository.team(teamId).first { it?.members?.getOrNull(1) != null }!!
        assertEquals(top.candidateLabel, updatedTeam.members[1]?.speciesName)
        assertEquals(top.types, updatedTeam.members[1]?.types)
    }
}
