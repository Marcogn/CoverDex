package com.marcogn.coverdex.ui.surprise

import com.marcogn.coverdex.domain.generator.DEFAULT_CONSTRAINTS
import com.marcogn.coverdex.domain.generator.GeneratorConstraints
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.model.TypeChart

data class SurpriseMeUiState(
    val chart: TypeChart? = null,
    val pool: List<PokemonEntry> = emptyList(),
    val customs: List<TeamMember> = emptyList(),
    /** The optional anchor Pokémon the generator must keep — `lockedMembers` in
     * `teamGenerator.ts`. Always at the front of [result], in order. */
    val lockedMembers: List<TeamMember> = emptyList(),
    val constraints: GeneratorConstraints = DEFAULT_CONSTRAINTS,
    /** The generated team, empty until the first Generate tap. */
    val result: List<TeamMember> = emptyList(),
    /** `"tooFewPokemon"` when the pool couldn't fill every slot — resolved to a string resource
     * by the screen, matching `GeneratorResult.warning`'s own string-key convention. */
    val warning: String? = null,
    /** True while `generate()`/`regenerateSlot()` are running on [kotlinx.coroutines.Dispatchers.Default]
     * — see `docs/post-migration-review.md`, finding 2. Drives the Generate button's spinner and
     * disables every regenerate action so a second tap can't race the one in flight. */
    val isGenerating: Boolean = false,
) {
    val anchorCount: Int get() = lockedMembers.size
    val constraintTotal: Int get() = with(constraints) { starterSlots + legendaryMythicalSlots + megaSlots + dynamaxSlots + customSlots }
    val remainingSlots: Int get() = (6 - anchorCount - constraintTotal).coerceAtLeast(0)
    val budgetFull: Boolean get() = anchorCount + constraintTotal >= 6
    val canGenerate: Boolean get() = chart != null && !isGenerating
}
