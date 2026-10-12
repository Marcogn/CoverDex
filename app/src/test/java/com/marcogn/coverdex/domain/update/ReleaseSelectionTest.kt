package com.marcogn.coverdex.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSelectionTest {

    private fun asset(name: String) = ReleaseAsset(name, "https://example.com/$name", 10, null)

    private fun release(
        tag: String = "v1.1.0",
        assets: List<ReleaseAsset> = listOf(asset("App-1.1.0.apk")),
        body: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = ReleaseInfo(tag, null, body, "https://example.com/release", draft, prerelease, assets)

    // --- APK asset picker ---

    @Test
    fun `picks the single apk next to a mapping file`() {
        val apk = asset("ThePatientGamerHelper-1.1.0.apk")
        assertEquals(ApkAssetPick.Found(apk), pickApkAsset(listOf(asset("ThePatientGamerHelper-1.1.0-mapping.txt"), apk)))
    }

    @Test
    fun `picks by extension whatever the name, case-insensitively`() {
        val apk = asset("renamed-release.APK")
        assertEquals(ApkAssetPick.Found(apk), pickApkAsset(listOf(apk)))
    }

    @Test
    fun `no apk or several apks is no pick`() {
        assertEquals(ApkAssetPick.None, pickApkAsset(emptyList()))
        assertEquals(ApkAssetPick.None, pickApkAsset(listOf(asset("mapping.txt"), asset("app.apk.sha256"))))
        assertEquals(ApkAssetPick.Ambiguous, pickApkAsset(listOf(asset("a.apk"), asset("b.apk"))))
    }

    // --- Release body cleanup ---

    @Test
    fun `bullets lose their marker and bold, other lines are dropped`() {
        val body = """
            |- **Bold headline.**
            |* Plain first sentence with no bold lead-in.
            |  - nested detail, not a headline
            |
            |Some trailing paragraph.
            |See [CHANGELOG.md](https://github.com/o/r/blob/main/CHANGELOG.md#110) for the full changelog.
        """.trimMargin()

        val notes = parseReleaseNotes(body)

        assertEquals(listOf("Bold headline.", "Plain first sentence with no bold lead-in."), notes.headlines)
        assertEquals("https://github.com/o/r/blob/main/CHANGELOG.md#110", notes.fullChangelogUrl)
    }

    @Test
    fun `windows line endings and a missing link are handled`() {
        val notes = parseReleaseNotes("- **One.**\r\n- **Two.**\r\n")
        assertEquals(listOf("One.", "Two."), notes.headlines)
        assertNull(notes.fullChangelogUrl)
    }

    @Test
    fun `a null or blank body has no headlines`() {
        assertEquals(ReleaseNotes(emptyList(), null), parseReleaseNotes(null))
        assertEquals(ReleaseNotes(emptyList(), null), parseReleaseNotes("  \n"))
    }

    @Test
    fun `links to other files are not the changelog`() {
        assertNull(parseReleaseNotes("See [README](https://example.com/README.md).").fullChangelogUrl)
    }

    // --- Evaluation ---

    @Test
    fun `a newer tag with one apk is an update`() {
        val r = release(tag = "v1.0.10", body = "- **New.**")
        val evaluation = evaluateRelease(r, installedVersionName = "1.0.9")
        assertTrue(evaluation is ReleaseEvaluation.Newer)
        val update = (evaluation as ReleaseEvaluation.Newer).update
        assertEquals(AppVersion(1, 0, 10), update.version)
        assertEquals(r.assets.single(), update.apk)
        assertEquals(listOf("New."), update.notes.headlines)
    }

    @Test
    fun `same or older tag is up to date, whatever the assets`() {
        assertEquals(ReleaseEvaluation.UpToDate, evaluateRelease(release(tag = "v1.1.0"), "1.1.0"))
        assertEquals(ReleaseEvaluation.UpToDate, evaluateRelease(release(tag = "v1.0.9", assets = emptyList()), "1.0.10"))
    }

    @Test
    fun `non-comparable versions, drafts and pre-releases are unusable`() {
        assertEquals(
            ReleaseEvaluation.Unusable(UnusableReleaseReason.VERSION_NOT_COMPARABLE),
            evaluateRelease(release(tag = "v1.2.0-beta"), "1.1.0"),
        )
        assertEquals(
            ReleaseEvaluation.Unusable(UnusableReleaseReason.VERSION_NOT_COMPARABLE),
            evaluateRelease(release(tag = "v1.2.0"), "1.1.0-debug"),
        )
        assertEquals(
            ReleaseEvaluation.Unusable(UnusableReleaseReason.NOT_A_STABLE_RELEASE),
            evaluateRelease(release(prerelease = true), "1.0.0"),
        )
        assertEquals(
            ReleaseEvaluation.Unusable(UnusableReleaseReason.NOT_A_STABLE_RELEASE),
            evaluateRelease(release(draft = true), "1.0.0"),
        )
    }

    @Test
    fun `a newer release without exactly one apk is unusable`() {
        assertEquals(
            ReleaseEvaluation.Unusable(UnusableReleaseReason.NO_APK_ASSET),
            evaluateRelease(release(assets = listOf(asset("mapping.txt"))), "1.0.0"),
        )
        assertEquals(
            ReleaseEvaluation.Unusable(UnusableReleaseReason.SEVERAL_APK_ASSETS),
            evaluateRelease(release(assets = listOf(asset("a.apk"), asset("b.apk"))), "1.0.0"),
        )
    }

    // --- Signer compatibility ---

    @Test
    fun `single signer matches when the candidate history holds the installed current signer`() {
        val installed = SignerSet(listOf("aa"), multipleSigners = false)
        assertTrue(signersCompatible(installed, SignerSet(listOf("aa"), false)))
        assertTrue(signersCompatible(installed, SignerSet(listOf("aa", "bb"), false)))
        assertFalse(signersCompatible(installed, SignerSet(listOf("bb"), false)))
    }

    @Test
    fun `single signer refuses a forked lineage and a rollback to an older key`() {
        val rotated = SignerSet(listOf("aa", "bb"), multipleSigners = false)
        assertTrue(signersCompatible(rotated, SignerSet(listOf("aa", "bb"), false)))
        assertFalse(signersCompatible(rotated, SignerSet(listOf("aa", "cc"), false)))
        assertFalse(signersCompatible(rotated, SignerSet(listOf("aa"), false)))
    }

    @Test
    fun `multiple signers must be the same set`() {
        val installed = SignerSet(listOf("aa", "bb"), multipleSigners = true)
        assertTrue(signersCompatible(installed, SignerSet(listOf("bb", "aa"), true)))
        assertFalse(signersCompatible(installed, SignerSet(listOf("aa"), true)))
        assertFalse(signersCompatible(installed, SignerSet(listOf("aa"), false)))
    }

    @Test
    fun `unreadable certificates are never compatible`() {
        assertFalse(signersCompatible(SignerSet(emptyList(), false), SignerSet(emptyList(), false)))
        assertFalse(signersCompatible(SignerSet(listOf("aa"), false), SignerSet(emptyList(), false)))
    }
}
