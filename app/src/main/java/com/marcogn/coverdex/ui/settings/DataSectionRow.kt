package com.marcogn.coverdex.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.CacheStatus
import com.marcogn.coverdex.domain.model.SyncState
import com.marcogn.coverdex.ui.common.displayName
import java.text.DateFormat
import java.util.Date

private const val SHORT_REVISION_LENGTH = 7

@Composable
fun DataSectionRow(
    cacheStatus: CacheStatus,
    syncState: SyncState,
    onSyncNow: () -> Unit,
    onClearCache: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.settings_section_data)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = if (cacheStatus.isUsable) {
                    val summaryRes = if (cacheStatus.moveCount == 1) R.string.settings_data_summary_one else R.string.settings_data_summary_other
                    stringResource(summaryRes, cacheStatus.speciesCount, cacheStatus.moveCount)
                } else {
                    stringResource(R.string.settings_data_not_synced)
                },
            )
            cacheStatus.syncedAtEpochMillis?.let { syncedAt ->
                Text(
                    text = stringResource(R.string.settings_data_synced_at, formatTimestamp(syncedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cacheStatus.datasetRevision?.let { revision ->
                Text(
                    text = stringResource(R.string.settings_data_revision, revision.take(SHORT_REVISION_LENGTH)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (syncState) {
            is SyncState.Running -> {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_data_syncing, syncState.stage.displayName()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { syncState.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
            is SyncState.Failed -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_data_sync_failed, syncState.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSyncNow) { Text(stringResource(R.string.settings_data_retry)) }
                }
            }
            else -> {}
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onSyncNow) { Text(stringResource(R.string.settings_data_sync_now)) }
            TextButton(onClick = { showClearConfirm = true }) { Text(stringResource(R.string.settings_data_clear)) }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_data_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_data_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearCache()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

// java.text.SimpleDateFormat rather than java.time: minSdk stays 24 (docs/plan/native-spec.md,
// "Identity") and java.time needs API 26 without desugaring.
private fun formatTimestamp(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
