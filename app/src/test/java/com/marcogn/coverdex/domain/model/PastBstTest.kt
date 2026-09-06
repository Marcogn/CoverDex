package com.marcogn.coverdex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PastBstTest {

    private fun entry(id: Int, baseStatTotal: Int) = PokemonEntry(
        id = id, name = "p$id", displayName = "P$id", speciesId = id, speciesName = "p$id",
        types = PokemonType.NORMAL to null, isLegendary = false, isMythical = false, isFinalEvolution = true,
        generationIntroduced = 1, defaultAbility = null, isDefaultForm = true, baseStatTotal = baseStatTotal,
    )

    @Test
    fun `null generation always resolves to the current baseStatTotal`() {
        val resolver = bstResolverFor(pastBst = emptyList(), generation = null)

        assertEquals(500, resolver(entry(id = 1, baseStatTotal = 500)))
    }

    @Test
    fun `a form with no historical rows resolves to its current baseStatTotal at any generation`() {
        val resolver = bstResolverFor(pastBst = emptyList(), generation = 3)

        assertEquals(500, resolver(entry(id = 1, baseStatTotal = 500)))
    }

    @Test
    fun `a generation at or after the only breakpoint uses the current value`() {
        // Alakazam-shaped: current 500, held at 490 through gen 5.
        val past = listOf(PastBst(pokemonId = 65, generationId = 5, bst = 490))
        val resolver = bstResolverFor(past, generation = 6)

        assertEquals(500, resolver(entry(id = 65, baseStatTotal = 500)))
    }

    @Test
    fun `a generation at or before the only breakpoint uses the historical value`() {
        val past = listOf(PastBst(pokemonId = 65, generationId = 5, bst = 490))

        assertEquals(490, bstResolverFor(past, generation = 5)(entry(id = 65, baseStatTotal = 500)))
        assertEquals(490, bstResolverFor(past, generation = 1)(entry(id = 65, baseStatTotal = 500)))
    }

    @Test
    fun `a generation strictly between two breakpoints uses the smallest breakpoint at or after it`() {
        // Two independent stat changes at gen 5 and gen 7 (see phase-7-...md §2.2's proof that
        // the combined total is constant between consecutive breakpoints).
        val past = listOf(
            PastBst(pokemonId = 1, generationId = 5, bst = 400),
            PastBst(pokemonId = 1, generationId = 7, bst = 420),
        )
        val resolver = bstResolverFor(past, generation = 6)

        // Smallest breakpoint >= 6 is 7 -> 420, matching gen 6 and gen 7 alike.
        assertEquals(420, resolver(entry(id = 1, baseStatTotal = 450)))
        assertEquals(420, bstResolverFor(past, generation = 7)(entry(id = 1, baseStatTotal = 450)))
    }

    @Test
    fun `different forms' breakpoints do not cross-contaminate`() {
        val past = listOf(
            PastBst(pokemonId = 1, generationId = 5, bst = 400),
            PastBst(pokemonId = 2, generationId = 5, bst = 999),
        )
        val resolver = bstResolverFor(past, generation = 1)

        assertEquals(400, resolver(entry(id = 1, baseStatTotal = 500)))
        // Form 3 has no historical rows at all, unaffected by form 1's or form 2's breakpoints.
        assertEquals(500, resolver(entry(id = 3, baseStatTotal = 500)))
    }
}
