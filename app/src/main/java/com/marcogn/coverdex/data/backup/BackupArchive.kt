package com.marcogn.coverdex.data.backup

import com.marcogn.coverdex.domain.backup.BackupPayload
import com.marcogn.coverdex.domain.backup.toBackupPayload
import com.marcogn.coverdex.domain.backup.toJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DATA_ENTRY = "data.json"

/** A single archive holds `data.json` — every team and the custom roster, nothing else. No
 * `images/` entries: unlike Hall of Memories, CoverDex has no per-entry photos to carry along. */
@Singleton
class BackupArchiveBuilder @Inject constructor() {
    suspend fun build(payload: BackupPayload): ByteArray = withContext(Dispatchers.IO) {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(payload.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        buffer.toByteArray()
    }
}

class BackupArchiveMissingDataException : Exception("Invalid backup: missing data.json in the archive")

@Singleton
class BackupArchiveReader @Inject constructor() {
    /** @throws BackupArchiveMissingDataException if the zip has no `data.json` entry.
     * @throws com.marcogn.coverdex.domain.backup.BackupFormatTooNewException if its
     * `formatVersion` is newer than this build supports. */
    suspend fun read(bytes: ByteArray): BackupPayload = withContext(Dispatchers.IO) {
        var payload: BackupPayload? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == DATA_ENTRY) {
                    payload = zip.readBytes().toString(Charsets.UTF_8).toBackupPayload()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        payload ?: throw BackupArchiveMissingDataException()
    }
}
