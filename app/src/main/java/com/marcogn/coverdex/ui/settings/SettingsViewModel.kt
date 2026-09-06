package com.marcogn.coverdex.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.data.backup.LocalBackupManager
import com.marcogn.coverdex.data.settings.SettingsPreferences
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.domain.repository.PokedexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val EMPTY_CACHE_STATUS = CacheStatus(isUsable = false, syncedAtEpochMillis = null, speciesCount = 0, moveCount = 0, datasetRevision = null)

data class SettingsUiState(
    val cacheStatus: CacheStatus = EMPTY_CACHE_STATUS,
    val syncState: SyncState = SyncState.Idle,
    val includeMegaDynamax: Boolean = false,
    /** The UI's own framing — stored inverted as `SettingsPreferences.excludeLegendaries`
     * (`docs/plan/phase-5-import-export-and-settings.md` §3). */
    val includeLegendaries: Boolean = true,
    val backupBusy: Boolean = false,
    /** Set only on a failed export/import — cleared on the next attempt. Never a success message:
     * a successful restore is visible immediately in the Teams/Roster lists it just replaced. */
    val backupMessage: String? = null,
    /** How many ranked suggestions the Analysis tab shows, 5-10 — Phase 7, see
     * docs/plan/phase-7-accuracy-and-customization.md §6. */
    val suggestionCount: Int = 5,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pokedexRepository: PokedexRepository,
    private val settingsPreferences: SettingsPreferences,
    private val localBackupManager: LocalBackupManager,
) : ViewModel() {

    private val backupBusy = MutableStateFlow(false)
    private val backupMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(pokedexRepository.cacheStatus, pokedexRepository.syncState) { cacheStatus, syncState -> cacheStatus to syncState },
        combine(
            settingsPreferences.includeMegaDynamax,
            settingsPreferences.excludeLegendaries,
            settingsPreferences.suggestionCount,
        ) { includeMega, excludeLegendaries, suggestionCount -> Triple(includeMega, excludeLegendaries, suggestionCount) },
        backupBusy,
        backupMessage,
    ) { (cacheStatus, syncState), (includeMegaDynamax, excludeLegendaries, suggestionCount), busy, message ->
        SettingsUiState(
            cacheStatus = cacheStatus,
            syncState = syncState,
            includeMegaDynamax = includeMegaDynamax,
            includeLegendaries = !excludeLegendaries,
            backupBusy = busy,
            backupMessage = message,
            suggestionCount = suggestionCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun syncNow() = pokedexRepository.forceResync()

    fun clearCache() {
        viewModelScope.launch { pokedexRepository.wipeCache() }
    }

    fun setIncludeMegaDynamax(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setIncludeMegaDynamax(enabled) }
    }

    fun setIncludeLegendaries(enabled: Boolean) {
        viewModelScope.launch { settingsPreferences.setExcludeLegendaries(!enabled) }
    }

    fun setSuggestionCount(count: Int) {
        viewModelScope.launch { settingsPreferences.setSuggestionCount(count) }
    }

    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            backupBusy.value = true
            backupMessage.value = null
            runCatching { localBackupManager.exportTo(destination) }
                .onFailure { backupMessage.value = it.message }
            backupBusy.value = false
        }
    }

    /** Failure is always one of [BackupArchiveMissingDataException] (not a zip this app wrote) or
     * [BackupFormatTooNewException] (a backup from a newer app version) — both carry a message
     * specific enough to show directly, so there is nothing to branch on here. */
    fun importBackup(source: Uri) {
        viewModelScope.launch {
            backupBusy.value = true
            backupMessage.value = null
            runCatching { localBackupManager.importFrom(source) }
                .onFailure { error -> backupMessage.value = error.message }
            backupBusy.value = false
        }
    }
}
