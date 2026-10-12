package com.marcogn.coverdex

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.data.update.UpdateManager
import com.marcogn.coverdex.domain.model.ThemeMode
import com.marcogn.coverdex.ui.navigation.CoverDexNavGraph
import com.marcogn.coverdex.ui.theme.CoverDexTheme
import com.marcogn.coverdex.ui.theme.ThemeViewModel
import com.marcogn.coverdex.ui.update.UpdateDialogHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * `AppCompatActivity`, not `ComponentActivity`: `AppCompatDelegate.setApplicationLocales()`
 * (the in-app language picker, Settings) silently does nothing with the latter. Doesn't
 * introduce View/XML: `setContent {}` remains the only UI entry point. See CLAUDE.md,
 * "Known gotchas".
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Phase 8: startup update check, at most once per process (UpdateManager guards it, so an
        // activity recreation -- rotation, language change -- does not check again). Never in debug
        // builds: they are signed with the debug key, so a release APK could not install over them;
        // "Check now" in Info stays available there to try the flow.
        if (!BuildConfig.DEBUG) {
            updateManager.checkIfDue()
        }
        setContent {
            CoverDexApp()
        }
    }
}

@Composable
private fun CoverDexApp(themeViewModel: ThemeViewModel = hiltViewModel()) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CoverDexTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CoverDexNavGraph()
            UpdateDialogHost()
        }
    }
}
