package com.marcogn.coverdex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.pokeapi.SyncStage

/** Enum labels are not on the enum — this keeps `domain/` free of Android imports. See
 * CLAUDE.md's code conventions. */
@Composable
fun SyncStage.displayName(): String = when (this) {
    SyncStage.DOWNLOADING -> stringResource(R.string.sync_stage_downloading)
    SyncStage.PARSING -> stringResource(R.string.sync_stage_parsing)
    SyncStage.WRITING -> stringResource(R.string.sync_stage_writing)
}
