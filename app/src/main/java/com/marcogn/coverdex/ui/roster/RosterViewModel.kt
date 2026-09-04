package com.marcogn.coverdex.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RosterViewModel @Inject constructor(
    private val customPokemonRepository: CustomPokemonRepository,
) : ViewModel() {

    val roster: StateFlow<List<TeamMember>> = customPokemonRepository.roster.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun delete(id: String) {
        viewModelScope.launch { customPokemonRepository.delete(id) }
    }
}
