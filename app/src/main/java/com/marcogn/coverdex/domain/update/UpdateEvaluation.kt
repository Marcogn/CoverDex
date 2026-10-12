package com.marcogn.coverdex.domain.update

/** A release that is newer than the installed app and has exactly one APK to download. */
data class AvailableUpdate(
    val version: AppVersion,
    val release: ReleaseInfo,
    val apk: ReleaseAsset,
    val notes: ReleaseNotes,
)

sealed interface ReleaseEvaluation {
    data class Newer(val update: AvailableUpdate) : ReleaseEvaluation
    data object UpToDate : ReleaseEvaluation
    data class Unusable(val reason: UnusableReleaseReason) : ReleaseEvaluation
}

enum class UnusableReleaseReason {
    /** The installed `versionName` or the release tag is not a plain `x.y.z`. */
    VERSION_NOT_COMPARABLE,
    /** Draft or pre-release: `releases/latest` never returns them, checked anyway. */
    NOT_A_STABLE_RELEASE,
    NO_APK_ASSET,
    SEVERAL_APK_ASSETS,
}

/**
 * Decides whether [release] is an update for the installed [installedVersionName]. Every doubt is
 * "no update" ([ReleaseEvaluation.Unusable]): offering the wrong file is worse than offering none.
 * The version is checked before the assets, so an older or equal release with odd assets is simply
 * [ReleaseEvaluation.UpToDate].
 */
fun evaluateRelease(release: ReleaseInfo, installedVersionName: String): ReleaseEvaluation {
    if (release.isDraft || release.isPrerelease) {
        return ReleaseEvaluation.Unusable(UnusableReleaseReason.NOT_A_STABLE_RELEASE)
    }
    val installed = AppVersion.parse(installedVersionName)
    val candidate = AppVersion.parse(release.tagName)
    if (installed == null || candidate == null) {
        return ReleaseEvaluation.Unusable(UnusableReleaseReason.VERSION_NOT_COMPARABLE)
    }
    if (candidate <= installed) return ReleaseEvaluation.UpToDate
    return when (val pick = pickApkAsset(release.assets)) {
        is ApkAssetPick.Found -> ReleaseEvaluation.Newer(
            AvailableUpdate(
                version = candidate,
                release = release,
                apk = pick.asset,
                notes = parseReleaseNotes(release.body),
            ),
        )
        ApkAssetPick.None -> ReleaseEvaluation.Unusable(UnusableReleaseReason.NO_APK_ASSET)
        ApkAssetPick.Ambiguous -> ReleaseEvaluation.Unusable(UnusableReleaseReason.SEVERAL_APK_ASSETS)
    }
}

/**
 * The signing certificates of a package, as SHA-256 hex fingerprints. [multipleSigners] mirrors
 * `SigningInfo.hasMultipleSigners()`: then [fingerprints] are all current signers, otherwise the
 * certificate rotation history (original first, current last).
 */
data class SignerSet(val fingerprints: List<String>, val multipleSigners: Boolean)

/**
 * Whether [candidate] may replace [installed], checked *before* handing the APK to the installer so
 * the user gets a clear reason instead of Android's `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Multiple
 * signers: the sets must be equal (Android's own rule). Single signer: the candidate's rotation
 * history must contain the installed app's *current* signer (last of its history) — an exact match in
 * the usual case of one unrotated key, still true after a legitimate APK Signature Scheme v3 rotation,
 * false for a forked lineage (A→B installed, A→C offered) that Android would refuse too. A rollback to
 * an older key is refused (fail closed). Android repeats the authoritative check anyway; an empty set
 * (certificates not readable) is never compatible.
 */
fun signersCompatible(installed: SignerSet, candidate: SignerSet): Boolean {
    if (installed.fingerprints.isEmpty() || candidate.fingerprints.isEmpty()) return false
    if (installed.multipleSigners != candidate.multipleSigners) return false
    if (installed.multipleSigners) return installed.fingerprints.toSet() == candidate.fingerprints.toSet()
    return installed.fingerprints.last() in candidate.fingerprints
}
