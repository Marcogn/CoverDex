package com.marcogn.coverdex.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.data.update.ApkInstaller
import com.marcogn.coverdex.data.settings.SettingsPreferences
import com.marcogn.coverdex.data.update.InstallStart
import com.marcogn.coverdex.data.update.UpdateManager
import com.marcogn.coverdex.domain.update.DEFAULT_UPDATE_FREQUENCY
import com.marcogn.coverdex.domain.update.UpdateFrequency
import com.marcogn.coverdex.domain.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin UI layer over the process-wide `UpdateManager` (whose state is the single source of truth, so
 * the app-wide dialog host and the Settings frequency row can each own an instance). Holds the
 * "install unknown apps" round trip and the frequency setting.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val apkInstaller: ApkInstaller,
    private val preferences: SettingsPreferences,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updateManager.state

    val frequency: StateFlow<UpdateFrequency> = preferences.updateFrequency
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_UPDATE_FREQUENCY)

    /** One-shot: the "install unknown apps" screen to open, consumed by [onPermissionScreenLaunched]. */
    private val _permissionIntent = MutableStateFlow<Intent?>(null)
    val permissionIntent: StateFlow<Intent?> = _permissionIntent.asStateFlow()

    fun onFrequencySelected(value: UpdateFrequency) {
        viewModelScope.launch { preferences.setUpdateFrequency(value) }
    }

    fun onDownload() = updateManager.startDownload()
    fun onCancelDownload() = updateManager.cancelDownload()
    fun onDismiss() = updateManager.dismiss()

    fun onInstall() {
        viewModelScope.launch {
            val start = updateManager.install()
            if (start is InstallStart.PermissionRequired) _permissionIntent.value = start.settingsIntent
        }
    }

    /** The host launched the screen: forget the intent so a recreated activity doesn't launch it again. */
    fun onPermissionScreenLaunched() {
        _permissionIntent.value = null
    }

    /** Back from the settings screen: retry only if the toggle is now on, or it would reopen forever. */
    fun onPermissionScreenClosed() {
        if (apkInstaller.canInstall()) onInstall()
    }
}
