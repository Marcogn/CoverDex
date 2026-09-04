package com.marcogn.coverdex.domain.model

/**
 * The 18x18 type effectiveness matrix, attacker -> defender -> multiplier. Missing cells default
 * to 1.0 (neutral) — belt-and-braces only, since the pinned `type_efficacy.csv` has all
 * 18 x 18 = 324 cells, neutral entries included (see docs/plan/reference-pokedata.md §3).
 */
@JvmInline
value class TypeChart(private val table: Map<PokemonType, Map<PokemonType, Double>>) {
    fun multiplier(attacker: PokemonType, defender: PokemonType): Double =
        table[attacker]?.get(defender) ?: 1.0

    /** Every (attacker, defender, multiplier) cell — for persisting the chart to the cache, never
     * used by the coverage engine, which always goes through [multiplier]. */
    fun entries(): List<Triple<PokemonType, PokemonType, Double>> =
        table.flatMap { (attacker, row) -> row.map { (defender, factor) -> Triple(attacker, defender, factor) } }
}
