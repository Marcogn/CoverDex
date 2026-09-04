package com.marcogn.coverdex.ui.roster

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.coverdex.data.settings.ThemePreferences
import com.marcogn.coverdex.domain.model.AbilityEntry
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RosterEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customPokemonRepository: CustomPokemonRepository,
    private val pokedexRepository: PokedexRepository,
    themePreferences: ThemePreferences,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Destination.RosterEditor>()
    val customId: String? = route.customId

    /** `null` for a brand-new entry — no repository read needed, so this is just a constant
     * rather than a real subscription. See `SlotEditorViewModel.existingMember` for why this is
     * read once and never re-seeds an in-progress draft on a later emission. */
    val existingMember: StateFlow<TeamMember?> = customId?.let { id ->
        customPokemonRepository.roster
            .map { roster -> roster.find { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    } ?: MutableStateFlow(null)

    val showMoves: StateFlow<Boolean> = themePreferences.showMoves.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun searchAbilities(query: String): Flow<List<AbilityEntry>> = pokedexRepository.searchAbilities(query)

    fun searchMoves(query: String): Flow<List<MoveEntry>> = pokedexRepository.searchMoves(query)

    fun save(member: TeamMember, onSaved: () -> Unit) {
        viewModelScope.launch {
            customPokemonRepository.save(member)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = customId ?: return
        viewModelScope.launch {
            customPokemonRepository.delete(id)
            onDeleted()
        }
    }
}
