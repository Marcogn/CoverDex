package com.marcogn.coverdex.data.debug

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.CoverDexDatabase
import com.marcogn.coverdex.data.repository.CustomPokemonRepositoryImpl
import com.marcogn.coverdex.data.repository.TeamRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class DebugSeederTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var teamRepository: TeamRepositoryImpl
    private lateinit var customPokemonRepository: CustomPokemonRepositoryImpl
    private lateinit var seeder: DebugSeeder

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepositoryImpl(database.teamDao())
        customPokemonRepository = CustomPokemonRepositoryImpl(database.customPokemonDao())
        seeder = DebugSeeder(teamRepository, customPokemonRepository)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `seeding an empty database creates one partial team, one full team, and two roster entries`() = runTest {
        seeder.seed()

        val teams = teamRepository.teams.first()
        assertEquals(2, teams.size)
        val filledCounts = teams.map { team -> team.members.count { it != null } }.sorted()
        assertEquals(listOf(3, 6), filledCounts)

        assertEquals(2, customPokemonRepository.roster.first().size)
    }

    @Test
    fun `seeding never runs twice, even if called again`() = runTest {
        seeder.seed()
        seeder.seed()

        assertEquals(2, teamRepository.teams.first().size)
    }

    @Test
    fun `seeding is a no-op once any real team already exists`() = runTest {
        teamRepository.createTeam("A real user team")

        seeder.seed()

        val teams = teamRepository.teams.first()
        assertEquals(1, teams.size)
        assertTrue(customPokemonRepository.roster.first().isEmpty())
    }
}
