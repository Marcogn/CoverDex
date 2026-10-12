package com.marcogn.coverdex.ui.about

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.coverdex.R
import com.marcogn.coverdex.data.update.UpdateManager
import com.marcogn.coverdex.domain.update.UpdateCheckResult
import com.marcogn.coverdex.ui.update.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AboutUiState(
    val isChecking: Boolean = false,
    /** Outcome of the last "Check for updates" tap, already localized; `null` before the first one. */
    val checkMessage: String? = null,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    /**
     * Ignores the configured frequency (works with Never too). An available update also opens the
     * app-wide dialog, since `UpdateManager` moves to `Available`; here only the line below the button.
     */
    fun onCheckNow() {
        if (_uiState.value.isChecking) return
        _uiState.update { it.copy(isChecking = true, checkMessage = null) }
        viewModelScope.launch {
            val result = updateManager.checkNow()
            if (result is UpdateCheckResult.Failed) Log.w("AboutViewModel", "Update check failed: ${result.failure}")
            val message = result.toMessage(context)
            _uiState.update { it.copy(isChecking = false, checkMessage = message) }
        }
    }
}

internal fun UpdateCheckResult.toMessage(context: Context): String = when (this) {
    is UpdateCheckResult.Available -> context.getString(R.string.about_check_available, update.version.toString())
    UpdateCheckResult.UpToDate -> context.getString(R.string.about_check_up_to_date)
    UpdateCheckResult.NoRelease -> context.getString(R.string.about_check_no_release)
    is UpdateCheckResult.Failed -> context.getString(R.string.about_check_failed, failure.toMessage(context))
}
