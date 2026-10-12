package com.marcogn.coverdex.domain.update

private val VERSION_PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

/**
 * A plain `x.y.z` version, compared numerically component by component (`1.0.10 > 1.0.9`, unlike a
 * string comparison). Release tags are `v` + `versionName` (`v1.0.6`), so a leading `v` is accepted.
 * Anything else — pre-release suffixes (`1.1.0-beta`), four components, garbage — is "not comparable"
 * ([parse] returns `null`) and the caller offers no update: failing closed is safer than guessing an
 * order for a format the release workflow never produces.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String): AppVersion? {
            val match = VERSION_PATTERN.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            // toIntOrNull: a component too large for an Int is garbage, not a crash.
            return AppVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
            )
        }
    }
}
