package com.marcogn.coverdex.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.ThemeMode
import com.marcogn.coverdex.ui.common.CoverDexTopBar
import com.marcogn.coverdex.ui.theme.ThemeViewModel

@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    var language by remember { mutableStateOf(currentAppLanguage()) }
    val uiState by settingsViewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { CoverDexTopBar(title = stringResource(R.string.settings_title), onMenuClick = onMenuClick) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                ThemeOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system), themeMode, themeViewModel::setThemeMode)
                ThemeOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light), themeMode, themeViewModel::setThemeMode)
                ThemeOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark), themeMode, themeViewModel::setThemeMode)
            }
            SettingsSection(title = stringResource(R.string.settings_section_language)) {
                LanguageOption(AppLanguage.SYSTEM, stringResource(R.string.settings_language_system), language) {
                    language = it
                    applyAppLanguage(it)
                }
                LanguageOption(AppLanguage.ITALIAN, stringResource(R.string.settings_language_italian), language) {
                    language = it
                    applyAppLanguage(it)
                }
                LanguageOption(AppLanguage.ENGLISH, stringResource(R.string.settings_language_english), language) {
                    language = it
                    applyAppLanguage(it)
                }
            }
            DataSectionRow(
                cacheStatus = uiState.cacheStatus,
                syncState = uiState.syncState,
                onSyncNow = settingsViewModel::syncNow,
                onClearCache = settingsViewModel::clearCache,
            )
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(modifier = Modifier.selectableGroup()) { content() }
    }
}

@Composable
private fun ThemeOption(mode: ThemeMode, label: String, selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    RadioRow(label = label, selected = selected == mode, onClick = { onSelect(mode) })
}

@Composable
private fun LanguageOption(language: AppLanguage, label: String, selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    RadioRow(label = label, selected = selected == language, onClick = { onSelect(language) })
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
