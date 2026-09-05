package com.marcogn.coverdex.data.backup

import com.marcogn.coverdex.domain.backup.BackupFormatTooNewException
import com.marcogn.coverdex.domain.backup.BackupPayload
import com.marcogn.coverdex.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.marcogn.coverdex.domain.backup.toBackupDto
import com.marcogn.coverdex.domain.buildMember
import com.marcogn.coverdex.domain.model.PokemonType
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = 26: see CLAUDE.md, "Known gotchas".
@Config(sdk = [26])
@RunWith(RobolectricTestRunner::class)
class BackupArchiveTest {

    private val builder = BackupArchiveBuilder()
    private val reader = BackupArchiveReader()

    @Test
    fun `write then read returns the same payload`() = runTest {
        val member = buildMember("Charizard", PokemonType.FIRE to PokemonType.FLYING, ability = "Blaze")
        val payload = BackupPayload(
            exportedAtEpochMillis = 1_700_000_000_000L,
            teams = emptyList(),
            customPokemon = listOf(member.toBackupDto()),
        )

        val bytes = builder.build(payload)
        val readBack = reader.read(bytes)

        assertEquals(payload, readBack)
    }

    @Test
    fun `a zip with no data entry is rejected`() = runTest {
        val emptyZip = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("unrelated.txt"))
                zip.closeEntry()
            }
        }.toByteArray()

        try {
            reader.read(emptyZip)
            fail("expected BackupArchiveMissingDataException")
        } catch (expected: BackupArchiveMissingDataException) {
            // expected
        }
    }

    @Test
    fun `a future-format payload is rejected on read, not on write`() = runTest {
        val payload = BackupPayload(formatVersion = CURRENT_BACKUP_FORMAT_VERSION + 1, exportedAtEpochMillis = 0L)
        val bytes = builder.build(payload)

        try {
            reader.read(bytes)
            fail("expected BackupFormatTooNewException")
        } catch (expected: BackupFormatTooNewException) {
            // expected
        }
    }
}
