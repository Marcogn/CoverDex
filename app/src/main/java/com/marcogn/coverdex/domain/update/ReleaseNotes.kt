package com.marcogn.coverdex.domain.update

/** What the update dialog shows: one line per headline, plus the link to the full changelog. */
data class ReleaseNotes(val headlines: List<String>, val fullChangelogUrl: String?)

private val BULLET = Regex("""^[-*+]\s+(.*)$""")
private val CHANGELOG_LINK = Regex("""\[[^\]]*CHANGELOG[^\]]*]\((https?://[^)\s]+)\)""", RegexOption.IGNORE_CASE)

/**
 * Turns the Release body `release.yml` publishes into [ReleaseNotes]. The body is a list of top-level
 * bullets (each a CHANGELOG bold headline, or the first sentence of a bullet with none) followed by
 * "See [CHANGELOG.md](…) for the full changelog." — so no Markdown parser is needed: bullet lines lose
 * their marker and the `**` emphasis; the CHANGELOG link becomes [ReleaseNotes.fullChangelogUrl];
 * every other line is dropped. Indented (nested) bullets are dropped too: only headlines are shown.
 */
fun parseReleaseNotes(body: String?): ReleaseNotes {
    if (body.isNullOrBlank()) return ReleaseNotes(headlines = emptyList(), fullChangelogUrl = null)
    val headlines = mutableListOf<String>()
    var changelogUrl: String? = null
    for (line in body.lineSequence()) {
        if (line.isNotEmpty() && line.first().isWhitespace()) continue
        val bullet = BULLET.matchEntire(line.trimEnd())
        if (bullet != null) {
            val text = bullet.groupValues[1].replace("**", "").trim()
            if (text.isNotEmpty()) headlines += text
            continue
        }
        if (changelogUrl == null) changelogUrl = CHANGELOG_LINK.find(line)?.groupValues?.get(1)
    }
    return ReleaseNotes(headlines = headlines, fullChangelogUrl = changelogUrl)
}
