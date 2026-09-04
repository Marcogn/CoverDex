package com.marcogn.coverdex.ui.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.coverdex.data.settings.ThemePreferences
import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import com.marcogn.coverdex.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SlotEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val teamRepository: TeamRepository,
    private val customPokemonRepository: CustomPokemonRepository,
    private val pokedexRepository: PokedexRepository,
    themePreferences: ThemePreferences,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Destination.SlotEditor>()
    val teamId: String = route.teamId
    val slotIndex: Int = route.slotIndex

    /** The slot's current member, if any — read once by the screen to seed its local editing
     * draft. Kept live (not a one-shot suspend read) only so the screen has something to render
     * while this first emission is in flight; the screen must not re-seed its draft on a later
     * emission, or the user's in-progress edits would be silently overwritten by their own save. */
    val existingMember: StateFlow<TeamMember?> = teamRepository.team(teamId)
        .map { team -> team?.members?.getOrNull(slotIndex) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Global, not per-team — see `data/settings/ThemePreferences.kt`'s `showMoves` doc. */
    val showMoves: StateFlow<Boolean> = themePreferences.showMoves.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun searchSpecies(query: String): Flow<List<PokemonEntry>> = pokedexRepository.searchSpecies(query)

    fun searchAbilities(query: String): Flow<List<AbilityEntry>> = pokedexRepository.searchAbilities(query)

    fun searchMoves(query: String): Flow<List<MoveEntry>> = pokedexRepository.searchMoves(query)

    fun save(member: TeamMember, onSaved: () -> Unit) {
        viewModelScope.launch {
            teamRepository.saveMember(teamId, slotIndex, member)
            onSaved()
        }
    }

    fun clearSlot(onCleared: () -> Unit) {
        viewModelScope.launch {
            teamRepository.clearSlot(teamId, slotIndex)
            onCleared()
        }
    }

    fun saveToRoster(member: TeamMember) {
        viewModelScope.launch { customPokemonRepository.save(member) }
    }
}
