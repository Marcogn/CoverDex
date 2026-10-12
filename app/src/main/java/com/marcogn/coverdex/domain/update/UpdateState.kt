package com.marcogn.coverdex.domain.update

import java.io.File

/** Where the update flow is; exposed by `UpdateManager.state` as a `StateFlow`. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    /** [totalBytes] comes from the release asset size; `0` when GitHub reported none. */
    data class Downloading(val update: AvailableUpdate, val bytesRead: Long, val totalBytes: Long) : UpdateState
    /** Downloaded, checksum and signature verified: waiting for `UpdateManager.install()`. */
    data class ReadyToInstall(val update: AvailableUpdate, val apkFile: File) : UpdateState
    /** Session committed; the system confirmation is on screen or about to be. */
    data class Installing(val update: AvailableUpdate, val apkFile: File) : UpdateState
    data class Failed(val failure: UpdateFailure) : UpdateState
}

/** Why something in the flow failed — enough for the UI to pick a message. */
sealed interface UpdateFailure {
    /** No connection, DNS, timeout, interrupted transfer. */
    data class Network(val detail: String?) : UpdateFailure
    /** 429, or 403 with the rate-limit headers, from the unauthenticated API (60 requests/hour per IP): try later. */
    data object RateLimited : UpdateFailure
    data class Http(val code: Int) : UpdateFailure
    /** The response could not be parsed: GitHub changed its shape, or not a release at all. */
    data class InvalidResponse(val detail: String?) : UpdateFailure
    data class UnusableRelease(val reason: UnusableReleaseReason) : UpdateFailure
    /** The downloaded file could not be stored (full or unwritable cache). */
    data object Storage : UpdateFailure
    data object SizeMismatch : UpdateFailure
    data object ChecksumMismatch : UpdateFailure
    data class VerificationFailed(val reason: ApkRejection) : UpdateFailure
    /** A `PackageInstaller` status other than success / user cancel, with the system's message. */
    data class InstallFailed(val status: Int, val message: String?) : UpdateFailure
}

enum class ApkRejection {
    /** `getPackageArchiveInfo` returned nothing: not an APK, or corrupted. */
    UNREADABLE,
    DIFFERENT_PACKAGE,
    NOT_NEWER,
    DIFFERENT_SIGNATURE,
}

/** What `UpdateManager.checkNow()` returns to the caller (the Info screen shows it). */
sealed interface UpdateCheckResult {
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    /** The repository has no published release yet (HTTP 404). */
    data object NoRelease : UpdateCheckResult
    data class Failed(val failure: UpdateFailure) : UpdateCheckResult
}
