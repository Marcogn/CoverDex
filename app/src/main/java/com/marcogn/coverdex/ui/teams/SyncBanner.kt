package com.marcogn.coverdex.ui.teams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.SyncState

/**
 * Inline, non-blocking sync progress — the whole reason this phase exists. Unlike the PWA's
 * `LoadingScreen`, which blocks the entire UI for its ~426 MB first-launch download, this sync is
 * ~208 KB and finishes in a couple of seconds, so it renders as a small banner the user can
 * ignore, not a gate. Renders nothing outside [SyncState.Running] — no success/failure chrome
 * here, that detail lives in Settings -> Data.
 */
@Composable
fun SyncBanner(syncState: SyncState, modifier: Modifier = Modifier) {
    if (syncState !is SyncState.Running) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.sync_banner_message), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { syncState.progress },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
