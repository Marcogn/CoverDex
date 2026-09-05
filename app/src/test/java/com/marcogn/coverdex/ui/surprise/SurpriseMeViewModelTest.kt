package com.marcogn.coverdex.ui.surprise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.MainDispatcherRule
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.repository.CustomPokemonRepositoryImpl
import com.marcogn.coverdex.data.repository.TeamRepositoryImpl
import com.marcogn.coverdex.domain.mockPokemonList
import com.marcogn.coverdex.domain.mockTypeChart
import com.marcogn.coverdex.ui.team.analysis.FakePokedexRepository
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
 * `docs/plan/phase-4-suggestions-and-generator.md` §4's Surprise Me deliverables: locking
 * anchors, generating, regenerating a single slot, and Keep writing the result into a new team.
 */
// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class SurpriseMeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: CoverDexDatabase
    private lateinit var teamRepository: TeamRepositoryImpl
    private lateinit var customPokemonRepository: CustomPokemonRepositoryImpl
    private val chart = mockTypeChart()
    private val pool = mockPokemonList()

    @Before
    fun createDependencies() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepositoryImpl(database.teamDao())
        customPokemonRepository = CustomPokemonRepositoryImpl(database.customPokemonDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun viewModel() = SurpriseMeViewModel(
        pokedexRepository = FakePokedexRepository(chart, pool = pool),
        customPokemonRepository = customPokemonRepository,
        teamRepository = teamRepository,
    )

    @Test
    fun `locking and removing an anchor updates lockedMembers`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        val snorlax = pool.first { it.name == "snorlax" }

        vm.addLocked(snorlax)
        val withAnchor = vm.uiState.first { it.lockedMembers.isNotEmpty() }
        assertEquals("Snorlax", withAnchor.lockedMembers.first().speciesName)

        vm.removeLocked(0)
        val withoutAnchor = vm.uiState.first { it.lockedMembers.isEmpty() }
        assertTrue(withoutAnchor.lockedMembers.isEmpty())
    }

    @Test
    fun `generate fills the result from the pool and keeps a locked anchor first`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        val charizard = pool.first { it.name == "charizard" }
        vm.addLocked(charizard)
        vm.uiState.first { it.lockedMembers.isNotEmpty() && it.chart != null }

        vm.generate()

        val state = vm.uiState.first { it.result.isNotEmpty() }
        assertEquals("Charizard", state.result.first().speciesName)
        assertTrue(state.result.size <= 6)
    }

    @Test
    fun `regenerateSlot only changes the targeted non-locked slot`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        vm.uiState.first { it.chart != null }
        vm.generate()
        val before = vm.uiState.first { it.result.isNotEmpty() }.result

        vm.regenerateSlot(0)

        // regenerateSlot now runs on Dispatchers.Default (docs/post-migration-review.md, finding
        // 2), so the update is no longer synchronous. isGenerating flips to true synchronously,
        // before regenerateSlot() even returns, and back to false only once the background work
        // finishes and writes the new result — waiting for it to go false again cannot match the
        // pre-regeneration state the way waiting on result.isNotEmpty() alone could (that
        // predicate was already true from the generate() call above). Asserting inequality
        // against `before` would still be wrong: the (unseeded) generator can legally reselect
        // slot 0's own prior occupant.
        val after = vm.uiState.first { !it.isGenerating && it.result.isNotEmpty() }.result
        assertEquals(before.size, after.size)
        for (i in 1 until before.size) {
            assertEquals(before[i].speciesName, after[i].speciesName)
        }
    }

    @Test
    fun `keep creates a new team and writes every generated member into its slots`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        vm.uiState.first { it.chart != null }
        vm.generate()
        val generated = vm.uiState.first { it.result.isNotEmpty() }.result

        vm.keep("Surprise Team") { }

        // keep() runs in viewModelScope.launch (fire-and-forget from the screen's point of
        // view) and writes createTeam() then six saveMember() calls in sequence, so wait until
        // the *last* expected slot is populated rather than just the team's existence — an
        // earlier Flow emission can land between createTeam() and the final saveMember().
        val team = teamRepository.teams
            .first { teams -> teams.any { it.name == "Surprise Team" && it.members.getOrNull(generated.size - 1) != null } }
            .first { it.name == "Surprise Team" }
        assertEquals("Surprise Team", team.name)
        for (i in generated.indices) {
            assertEquals(generated[i].speciesName, team.members[i]?.speciesName)
        }
        for (i in generated.size until 6) {
            assertEquals(null, team.members.getOrNull(i))
        }
    }
}
