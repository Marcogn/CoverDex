package com.marcogn.coverdex.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.domain.repository.PokedexRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import com.marcogn.coverdex.domain.showdown.ImportResult
import com.marcogn.coverdex.domain.showdown.importShowdownTeam
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportShowdownUiState(
    val inputText: String = "",
    /** `null` before the first Parse tap. */
    val result: ImportResult? = null,
) {
    val unknownMoveNames: List<String> get() = result?.members
        ?.flatMap { it.unknownMoveNames }
        ?.distinct()
        ?: emptyList()
}

/**
 * Backs `ImportShowdownScreen` (`docs/plan/phase-5-import-export-and-settings.md` §2): parse a
 * pasted or file-loaded Showdown paste, show what it resolved before committing, then create a
 * brand-new team from it — same "always a new team, named up front" shape as Surprise Me's Keep
 * (see `docs/implementation-decisions.md`, "Phase 4"), not an overwrite of an existing one.
 */
@HiltViewModel
class ImportShowdownViewModel @Inject constructor(
    private val pokedexRepository: PokedexRepository,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportShowdownUiState())
    val uiState: StateFlow<ImportShowdownUiState> = _uiState.asStateFlow()

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text, result = null) }
    }

    fun parse() {
        val text = _uiState.value.inputText
        viewModelScope.launch {
            val speciesByName = pokedexRepository.allSpecies().associateBy { it.displayName.lowercase() }
            val movesByName = pokedexRepository.allMoves().associateBy { it.displayName.lowercase() }
            val result = importShowdownTeam(
                text,
                resolveMove = { name -> movesByName[name.lowercase()] },
                resolveSpecies = { name -> speciesByName[name.lowercase()] },
            )
            _uiState.update { it.copy(result = result) }
        }
    }

    fun createTeam(name: String, onCreated: (String) -> Unit) {
        val members = _uiState.value.result?.members ?: return
        if (members.isEmpty()) return
        viewModelScope.launch {
            val teamId = teamRepository.createTeam(name)
            members.take(6).forEachIndexed { index, imported ->
                teamRepository.saveMember(teamId, index, imported.member)
            }
            onCreated(teamId)
        }
    }
}
