package com.marcogn.coverdex.domain.repository

import com.marcogn.coverdex.domain.backup.BackupPayload

/**
 * Produces and consumes a [BackupPayload] with no knowledge of where the bytes go — the same
 * seam Hall of Memories' `BackupRepository` keeps open for its own future backends.
 */
interface BackupRepository {

    /** Every team and the full custom roster, ignoring nothing. */
    suspend fun exportPayload(): BackupPayload

    /** Full replace inside one transaction: every existing team/member/move/custom-roster-entry
     * is gone before the restored ones land, ids and timestamps preserved. No merging, no
     * conflict resolution. */
    suspend fun importPayload(payload: BackupPayload)
}
