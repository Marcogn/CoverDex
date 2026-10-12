package com.marcogn.coverdex.domain.update

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures: `releases-latest-v1.0.6.json` is the real `GET /repos/Marcogn/ThePatientGamerHelper/
 * releases/latest` response of 2026-10-11, unedited; the `-no-digest-no-body` one is the same with
 * `body` and every asset `digest` set to JSON `null` (what older releases and assets look like).
 */
class ReleaseParsingTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("update/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `parses the real latest-release response`() {
        val release = parseLatestRelease(fixture("releases-latest-v1.0.6.json"))

        assertEquals("v1.0.6", release.tagName)
        assertEquals("ThePatientGamerHelper v1.0.6", release.name)
        assertEquals("https://github.com/Marcogn/ThePatientGamerHelper/releases/tag/v1.0.6", release.htmlUrl)
        assertFalse(release.isDraft)
        assertFalse(release.isPrerelease)
        val asset = release.assets.single()
        assertEquals("ThePatientGamerHelper-1.0.6.apk", asset.name)
        assertEquals(
            "https://github.com/Marcogn/ThePatientGamerHelper/releases/download/v1.0.6/ThePatientGamerHelper-1.0.6.apk",
            asset.downloadUrl,
        )
        assertEquals(14_659_121L, asset.sizeBytes)
        assertEquals("sha256:6a238e8d3522f1c6f85878c8f1b5d7d283d8fbd1fd6e848737bf4ecbb70fd766", asset.digest)
        assertTrue(release.body!!.startsWith("- **Fixed rapid navigation taps"))
    }

    @Test
    fun `explicit null body and digest decode as null`() {
        val release = parseLatestRelease(fixture("releases-latest-v1.0.6-no-digest-no-body.json"))

        assertNull(release.body)
        assertNull(release.assets.single().digest)
        assertNull(sha256FromDigest(release.assets.single().digest))
    }

    @Test
    fun `the real body becomes headlines and a full changelog link`() {
        val notes = parseReleaseNotes(parseLatestRelease(fixture("releases-latest-v1.0.6.json")).body)

        assertEquals(
            listOf(
                "Fixed rapid navigation taps landing on the wrong screen, and gave screen transitions a modern, faster feel.",
                "Fixed a `release.yml` bug that could burn a version number on a failed signed build.",
            ),
            notes.headlines,
        )
        assertEquals(
            "https://github.com/Marcogn/ThePatientGamerHelper/blob/main/CHANGELOG.md#106---2026-08-21",
            notes.fullChangelogUrl,
        )
    }

    @Test(expected = SerializationException::class)
    fun `a response without tag_name fails instead of guessing`() {
        parseLatestRelease("""{"html_url": "https://example.com", "assets": []}""")
    }

    @Test(expected = SerializationException::class)
    fun `an error body is not a release`() {
        parseLatestRelease("""{"message": "Not Found", "documentation_url": "https://docs.github.com"}""")
    }

    @Test
    fun `null assets decode as an empty list`() {
        val release = parseLatestRelease("""{"tag_name": "v1.0.0", "html_url": "https://x", "assets": null}""")
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `sha256FromDigest accepts only a well-formed sha256 digest`() {
        val hex = "6A238E8D3522F1C6F85878C8F1B5D7D283D8FBD1FD6E848737BF4ECBB70FD766"
        assertEquals(hex.lowercase(), sha256FromDigest("sha256:$hex"))
        assertNull(sha256FromDigest(null))
        assertNull(sha256FromDigest("sha512:$hex"))
        assertNull(sha256FromDigest("sha256:abc"))
        assertNull(sha256FromDigest(hex))
    }
}
