package com.marcogn.coverdex.ui.update

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.BuildConfig
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.update.AvailableUpdate
import com.marcogn.coverdex.domain.update.UpdateState

/**
 * The app-wide update dialogs, driven only by `UpdateManager.state`: available (headlines, "Full
 * changelog", Download / Not now) → progress (Cancel) → ready to install → system confirmation →
 * failure message. Sits above the nav graph so a startup check can ask on any screen.
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val permissionIntent by viewModel.permissionIntent.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onPermissionScreenClosed() }
    LaunchedEffect(permissionIntent) {
        permissionIntent?.let {
            permissionLauncher.launch(it)
            viewModel.onPermissionScreenLaunched()
        }
    }

    when (val current = state) {
        is UpdateState.Available -> AvailableDialog(
            update = current.update,
            onDownload = viewModel::onDownload,
            onNotNow = viewModel::onDismiss,
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading_title, current.update.version.toString())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current.totalBytes > 0) {
                        val fraction = (current.bytesRead.toFloat() / current.totalBytes).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.update_download_percent, (fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onCancelDownload) { Text(stringResource(R.string.update_cancel)) }
            },
        )
        is UpdateState.ReadyToInstall -> AlertDialog(
            // Not dismissible by tap outside/back: dismiss() deletes the verified APK, so only "Not now" does it.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_ready_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.update_ready_message, current.update.version.toString()))
                    // Below API 26 there is no per-app "install unknown apps" screen: the global setting
                    // applies and the installer itself refuses, so the app only explains it.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        Text(stringResource(R.string.update_unknown_sources_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onInstall) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.update_not_now)) }
            },
        )
        is UpdateState.Installing -> AlertDialog(
            onDismissRequest = viewModel::onDismiss,
            title = { Text(stringResource(R.string.update_installing_title)) },
            text = { Text(stringResource(R.string.update_installing_message)) },
            // "Close" is the escape if the system never reports a final status (see UpdateManager.dismiss).
            confirmButton = {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.update_close)) }
            },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = viewModel::onDismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(current.failure.toMessage(context)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.update_close)) }
            },
        )
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}

@Composable
private fun AvailableDialog(update: AvailableUpdate, onDownload: () -> Unit, onNotNow: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.update_available_versions, update.version.toString(), BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                )
                update.notes.headlines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                update.notes.fullChangelogUrl?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text(stringResource(R.string.update_full_changelog))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text(stringResource(R.string.update_download)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.update_not_now)) } },
    )
}
