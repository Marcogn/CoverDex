package com.marcogn.coverdex.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.dao.TeamDao
import com.marcogn.coverdex.data.local.entity.TeamEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberEntity
import com.marcogn.coverdex.data.local.entity.TeamMemberMoveEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class TeamDaoTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var dao: TeamDao

    private fun team(id: String, name: String, position: Int = 0) =
        TeamEntity(id = id, name = name, createdAtEpochMillis = 1000L, position = position)

    private fun member(id: String, teamId: String, slotIndex: Int, speciesName: String = "bulbasaur") =
        TeamMemberEntity(
            id = id,
            teamId = teamId,
            slotIndex = slotIndex,
            pokedexId = 1,
            speciesName = speciesName,
            type1 = "grass",
            type2 = "poison",
            ability = "overgrow",
            isCustomSaved = false,
        )

    private fun move(id: String, memberId: String, moveIndex: Int) =
        TeamMemberMoveEntity(
            id = id,
            memberId = memberId,
            moveIndex = moveIndex,
            name = "tackle",
            typeName = "normal",
            power = 40,
            damageClass = "PHYSICAL",
            isCustom = false,
        )

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.teamDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a created team round-trips through observeTeam with no members`() = runTest {
        dao.upsertTeam(team("t1", "My Team"))

        val loaded = dao.observeTeam("t1").first()

        assertEquals("My Team", loaded?.team?.name)
        assertTrue(loaded?.members?.isEmpty() == true)
    }

    @Test
    fun `renaming a team does not touch its members or their moves`() = runTest {
        dao.upsertTeam(team("t1", "Old Name"))
        dao.saveMember("t1", 0, member("m1", "t1", 0), listOf(move("mv1", "m1", 0)))

        dao.renameTeam("t1", "New Name")

        val loaded = dao.observeTeam("t1").first()
        assertEquals("New Name", loaded?.team?.name)
        assertEquals(1, loaded?.members?.size)
        assertEquals("bulbasaur", loaded?.members?.first()?.member?.speciesName)
        assertEquals(1, loaded?.members?.first()?.moves?.size)
    }

    private fun rowCount(table: String): Int =
        database.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `deleting a team cascades to its members and their moves`() = runTest {
        dao.upsertTeam(team("t1", "My Team"))
        dao.saveMember("t1", 0, member("m1", "t1", 0), listOf(move("mv1", "m1", 0)))

        dao.deleteTeam("t1")

        assertNull(dao.observeTeam("t1").first())
        // The member row itself must be gone too, not just unreachable via the deleted team.
        assertEquals(0, rowCount("team_member"))
        assertEquals(0, rowCount("team_member_move"))
    }

    @Test
    fun `saveMember on an already-filled slot replaces the member and its moves, keyed by slotIndex`() = runTest {
        dao.upsertTeam(team("t1", "My Team"))
        dao.saveMember("t1", 0, member("m1", "t1", 0, "bulbasaur"), listOf(move("mv1", "m1", 0)))

        dao.saveMember("t1", 0, member("m2", "t1", 0, "charmander"), listOf(move("mv2", "m2", 0)))

        val loaded = dao.observeTeam("t1").first()
        assertEquals(1, loaded?.members?.size)
        assertEquals("charmander", loaded?.members?.first()?.member?.speciesName)
        assertEquals(1, loaded?.members?.first()?.moves?.size)
        assertEquals("m2", loaded?.members?.first()?.moves?.first()?.memberId)
    }

    @Test
    fun `saveMember on one slot leaves the team's other slots untouched`() = runTest {
        dao.upsertTeam(team("t1", "My Team"))
        dao.saveMember("t1", 0, member("m1", "t1", 0, "bulbasaur"), emptyList())
        dao.saveMember("t1", 1, member("m2", "t1", 1, "charmander"), emptyList())

        dao.saveMember("t1", 0, member("m3", "t1", 0, "squirtle"), emptyList())

        val loaded = dao.observeTeam("t1").first()
        val bySlot = loaded?.members?.associateBy { it.member.slotIndex }
        assertEquals("squirtle", bySlot?.get(0)?.member?.speciesName)
        assertEquals("charmander", bySlot?.get(1)?.member?.speciesName)
    }

    @Test
    fun `clearSlot removes only that slot's member`() = runTest {
        dao.upsertTeam(team("t1", "My Team"))
        dao.saveMember("t1", 0, member("m1", "t1", 0), emptyList())
        dao.saveMember("t1", 1, member("m2", "t1", 1), emptyList())

        dao.clearSlot("t1", 0)

        val loaded = dao.observeTeam("t1").first()
        assertEquals(1, loaded?.members?.size)
        assertEquals(1, loaded?.members?.first()?.member?.slotIndex)
    }

    @Test
    fun `observeTeams orders by position then createdAt`() = runTest {
        dao.upsertTeam(team("t2", "Second", position = 1))
        dao.upsertTeam(team("t1", "First", position = 0))

        val loaded = dao.observeTeams().first()

        assertEquals(listOf("First", "Second"), loaded.map { it.team.name })
    }

    @Test
    fun `countTeams reflects the number of teams`() = runTest {
        assertEquals(0, dao.countTeams())
        dao.upsertTeam(team("t1", "My Team"))
        assertEquals(1, dao.countTeams())
    }
}
