package com.marcogn.coverdex.domain.item

import com.marcogn.coverdex.domain.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEffectsTest {

    @Test
    fun `itemKey lowercases and strips every symbol`() {
        assertEquals("airballoon", itemKey("Air Balloon"))
        assertEquals("airballoon", itemKey("air-balloon"))
        assertEquals("chilanberry", itemKey("Chilan Berry"))
    }

    @Test
    fun `getItemEffects returns null for null, empty, or an unmodelled item`() {
        assertNull(getItemEffects(null))
        assertNull(getItemEffects(""))
        assertNull(getItemEffects("Leftovers"))
        assertNull(getItemEffects("Heavy-Duty Boots"))
    }

    @Test
    fun `getItemEffects is case- and symbol-insensitive`() {
        assertEquals(getItemEffects("air-balloon"), getItemEffects("Air Balloon"))
    }

    @Test
    fun `every ITEM_EFFECTS entry round-trips through getItemEffects`() {
        for ((slug, effects) in ITEM_EFFECTS) {
            assertEquals(effects, getItemEffects(slug))
        }
    }

    @Test
    fun `ITEM_EFFECTS has one resist berry per type except Normal, plus Chilan for Normal`() {
        val resistBerryTypes = ITEM_EFFECTS.values
            .flatten()
            .filterIsInstance<ItemEffect.ResistBerry>()
            .map { it.type }
            .toSet()
        assertEquals(PokemonType.entries.toSet(), resistBerryTypes)

        val chilan = getItemEffects("chilan-berry")!!.single() as ItemEffect.ResistBerry
        assertEquals(PokemonType.NORMAL, chilan.type)
        assertTrue(chilan.alwaysApplies)
    }

    @Test
    fun `air-balloon, iron-ball and ring-target are each modelled once`() {
        assertEquals(listOf(ItemEffect.Immunity(PokemonType.GROUND)), getItemEffects("air-balloon"))
        assertEquals(listOf(ItemEffect.GroundsHolder), getItemEffects("iron-ball"))
        assertEquals(listOf(ItemEffect.RemovesTypeImmunities), getItemEffects("ring-target"))
    }
}
