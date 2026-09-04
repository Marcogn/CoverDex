package com.marcogn.coverdex.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.local.dao.CustomPokemonDao
import com.marcogn.coverdex.data.local.entity.CustomPokemonEntity
import com.marcogn.coverdex.data.local.entity.CustomPokemonMoveEntity
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
class CustomPokemonDaoTest {

    private lateinit var database: CoverDexDatabase
    private lateinit var dao: CustomPokemonDao

    private fun custom(id: String, name: String, createdAt: Long = 1000L) =
        CustomPokemonEntity(id = id, name = name, type1 = "normal", type2 = null, ability = null, createdAtEpochMillis = createdAt)

    private fun move(id: String, customId: String, moveIndex: Int) =
        CustomPokemonMoveEntity(
            id = id, customId = customId, moveIndex = moveIndex, name = "tackle",
            typeName = "normal", power = 40, damageClass = "PHYSICAL", isCustom = false,
        )

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CoverDexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.customPokemonDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a saved custom Pokemon round-trips with its moves`() = runTest {
        dao.upsert(custom("c1", "Custom Bulba"), listOf(move("mv1", "c1", 0)))

        val roster = dao.observeRoster().first()

        assertEquals(1, roster.size)
        assertEquals("Custom Bulba", roster.first().custom.name)
        assertEquals(1, roster.first().moves.size)
    }

    @Test
    fun `editing an entry replaces its moves without duplicating them`() = runTest {
        dao.upsert(custom("c1", "Custom Bulba"), listOf(move("mv1", "c1", 0)))

        dao.upsert(custom("c1", "Custom Bulba"), listOf(move("mv2", "c1", 0), move("mv3", "c1", 1)))

        val roster = dao.observeRoster().first()
        assertEquals(1, roster.size)
        assertEquals(2, roster.first().moves.size)
    }

    @Test
    fun `editing an entry's name does not reset its creation time`() = runTest {
        dao.upsert(custom("c1", "Custom Bulba", createdAt = 1000L), emptyList())

        // A later "edit" call passes a fresh createdAt, as a real caller's mapper would — it must
        // be ignored, since updateFields() never touches that column.
        dao.upsert(custom("c1", "Renamed", createdAt = 9999L), emptyList())

        val roster = dao.observeRoster().first()
        assertEquals("Renamed", roster.first().custom.name)
        assertEquals(1000L, roster.first().custom.createdAtEpochMillis)
    }

    @Test
    fun `deleting an entry cascades to its moves`() = runTest {
        dao.upsert(custom("c1", "Custom Bulba"), listOf(move("mv1", "c1", 0)))

        dao.delete("c1")

        assertTrue(dao.observeRoster().first().isEmpty())
    }
}
