package com.marcogn.coverdex.ui.team.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.MainDispatcherRule
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.repository.CustomPokemonRepositoryImpl
import com.marcogn.coverdex.data.repository.TeamRepositoryImpl
import com.marcogn.coverdex.data.settings.ThemePreferences
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.mockTypeChart
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
    private lateinit var themePreferences: ThemePreferences
    private val chart = mockTypeChart()

    @Before
    fun createDependencies() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepositoryImpl(database.teamDao())
        customPokemonRepository = CustomPokemonRepositoryImpl(database.customPokemonDao())
        themePreferences = ThemePreferences(context)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun viewModel(teamId: String) = AnalysisViewModel(
        savedStateHandle = SavedStateHandle(mapOf("teamId" to teamId)),
        teamRepository = teamRepository,
        pokedexRepository = FakePokedexRepository(chart),
        customPokemonRepository = customPokemonRepository,
        themePreferences = themePreferences,
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
        themePreferences.setShowMoves(false)
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
        themePreferences.setShowMoves(true)
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
        themePreferences.setShowMoves(false)
        val teamId = teamRepository.createTeam("T3")

        val empty = viewModel(teamId).uiState.first { it.chart != null }
        assertEquals(false, empty.canAnalyse)

        teamRepository.saveMember(teamId, 0, waterFlyingWithElectricMove())
        val filled = viewModel(teamId).uiState.first { it.members.isNotEmpty() }
        assertTrue(filled.canAnalyse)
    }
}
