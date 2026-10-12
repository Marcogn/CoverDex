package com.marcogn.coverdex.data.update

import android.content.pm.PackageInstaller
import android.util.Log
import com.marcogn.coverdex.BuildConfig
import com.marcogn.coverdex.data.settings.SettingsPreferences
import com.marcogn.coverdex.domain.update.ReleaseEvaluation
import com.marcogn.coverdex.domain.update.UpdateCheckPolicy
import com.marcogn.coverdex.domain.update.UpdateCheckResult
import com.marcogn.coverdex.domain.update.UpdateFailure
import com.marcogn.coverdex.domain.update.UpdateState
import com.marcogn.coverdex.domain.update.evaluateRelease
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "UpdateManager"

/**
 * The single entry point of the update flow: check → [UpdateState.Available] → [startDownload] →
 * [UpdateState.Downloading] → verified [UpdateState.ReadyToInstall] → [install] →
 * [UpdateState.Installing] → system confirmation. Process-wide singleton; the state lives in memory
 * only (a new process starts [UpdateState.Idle], and `ApkDownloader.deleteLeftovers()` removes any
 * file of the previous one).
 *
 * A *check* never moves the state to [UpdateState.Failed]: [checkIfDue] logs its errors (nothing is
 * shown at startup) and [checkNow] returns them, so the screen that asked can show them inline.
 * [UpdateState.Failed] is reserved for download/verification/install errors, after the user chose to
 * update.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val client: GitHubReleaseClient,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val preferences: SettingsPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val checkMutex = Mutex()
    private val startupCheckStarted = AtomicBoolean(false)
    private var downloadJob: Job? = null
    /** Identity of the current download; see `publish` in [startDownload]. */
    @Volatile private var activeDownload: Any? = null
    private val installStarting = AtomicBoolean(false)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * The startup check: runs at most once per process, and only if [SettingsPreferences.updateFrequency] says
     * it is due. Errors are logged and swallowed — offline at startup shows nothing. Runs in this
     * singleton's scope, not the caller's: an activity recreated mid-check (rotation, language change)
     * must not cancel the one check this process gets.
     */
    fun checkIfDue() {
        if (!startupCheckStarted.compareAndSet(false, true)) return
        scope.launch {
            if (!UpdateCheckPolicy.isDue(preferences.currentUpdateFrequency(), preferences.currentLastUpdateCheckAt(), Instant.now())) {
                return@launch
            }
            val result = check()
            if (result is UpdateCheckResult.Failed) Log.w(TAG, "Startup update check failed: ${result.failure}")
        }
    }

    /** "Check now": ignores the frequency (works with Never too) and returns the outcome, errors included. */
    suspend fun checkNow(): UpdateCheckResult = check()

    private suspend fun check(): UpdateCheckResult = checkMutex.withLock {
        // Already past the check (downloading, ready, installing): report it, don't disturb it.
        when (val current = _state.value) {
            is UpdateState.Downloading -> return UpdateCheckResult.Available(current.update)
            is UpdateState.ReadyToInstall -> return UpdateCheckResult.Available(current.update)
            is UpdateState.Installing -> return UpdateCheckResult.Available(current.update)
            else -> Unit
        }
        _state.value = UpdateState.Checking
        val result = try {
            val release = client.fetchLatestRelease()
            preferences.setLastUpdateCheckAt(Instant.now())
            if (release == null) {
                UpdateCheckResult.NoRelease
            } else {
                when (val evaluation = evaluateRelease(release, BuildConfig.VERSION_NAME)) {
                    is ReleaseEvaluation.Newer -> UpdateCheckResult.Available(evaluation.update)
                    ReleaseEvaluation.UpToDate -> UpdateCheckResult.UpToDate
                    is ReleaseEvaluation.Unusable ->
                        UpdateCheckResult.Failed(UpdateFailure.UnusableRelease(evaluation.reason))
                }
            }
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: UpdateException) {
            UpdateCheckResult.Failed(e.failure)
        } catch (e: Exception) {
            // Anything unforeseen still ends the check cleanly: a startup check must never crash.
            Log.w(TAG, "Unexpected update check error", e)
            UpdateCheckResult.Failed(UpdateFailure.InvalidResponse(e.message))
        }
        _state.value = if (result is UpdateCheckResult.Available) UpdateState.Available(result.update) else UpdateState.Idle
        result
    }

    /**
     * "Not now", or acknowledging a failure: back to [UpdateState.Idle]. The next due check asks again.
     * Also leaves [UpdateState.Installing]: if the system never reports a final status (some devices
     * on back/outside tap), the flow must not stay stuck for the rest of the process.
     */
    fun dismiss() {
        when (val current = _state.value) {
            is UpdateState.Available, is UpdateState.Failed -> _state.value = UpdateState.Idle
            is UpdateState.ReadyToInstall -> {
                _state.value = UpdateState.Idle
                scope.launch { downloader.delete(current.apkFile) }
            }
            is UpdateState.Installing -> {
                _state.value = UpdateState.Idle
                scope.launch { downloader.delete(current.apkFile) }
            }
            else -> Unit
        }
    }

    /** From [UpdateState.Available] only; ignored otherwise. */
    fun startDownload() {
        val available = _state.value as? UpdateState.Available ?: return
        val update = available.update
        val token = Any()
        activeDownload = token
        _state.value = UpdateState.Downloading(update, bytesRead = 0, totalBytes = update.apk.sizeBytes)
        downloadJob = scope.launch {
            // Writes only while this download is still the current one and the state still says
            // Downloading: a Cancel (or a newer download) that lands while this job is between two
            // suspension points must not be overwritten by a late event of this job.
            fun publish(next: UpdateState): Boolean {
                var applied = false
                _state.update { current ->
                    applied = activeDownload === token && current is UpdateState.Downloading
                    if (applied) next else current
                }
                return applied
            }
            var downloadedFile: File? = null
            var handedOver = false
            try {
                downloader.download(update).collect { event ->
                    when (event) {
                        is DownloadEvent.Progress ->
                            publish(UpdateState.Downloading(update, event.bytesRead, event.totalBytes))
                        is DownloadEvent.Completed -> {
                            downloadedFile = event.file
                            val rejection = verifier.verify(event.file)
                            handedOver = rejection == null && publish(UpdateState.ReadyToInstall(update, event.file))
                            if (rejection != null) publish(UpdateState.Failed(UpdateFailure.VerificationFailed(rejection)))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                publish(UpdateState.Failed(e.failure))
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                publish(UpdateState.Failed(UpdateFailure.Network(e.message)))
            } finally {
                // A complete file nobody holds (refused, or cancelled during verification) goes now,
                // not at the next process start.
                val orphan = downloadedFile
                if (!handedOver && orphan != null) withContext(NonCancellable) { downloader.delete(orphan) }
            }
        }
    }

    /** Cancel on the progress dialog: the partial file is deleted, back to [UpdateState.Available]. */
    fun cancelDownload() {
        val downloading = _state.value as? UpdateState.Downloading ?: return
        activeDownload = null
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Available(downloading.update)
    }

    /**
     * From [UpdateState.ReadyToInstall] only (`null` otherwise, and for a second call while the first
     * is still copying the APK into the session — a double tap must not commit two sessions).
     * [InstallStart.PermissionRequired]: the UI opens the per-app "install unknown apps" screen and
     * calls this again when the user is back (the state stays [UpdateState.ReadyToInstall], so a
     * refusal just leaves the dialog where it was).
     */
    suspend fun install(): InstallStart? {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return null
        if (!installStarting.compareAndSet(false, true)) return null
        return try {
            installer.install(ready.apkFile).also { start ->
                if (start is InstallStart.Committed) _state.value = UpdateState.Installing(ready.update, ready.apkFile)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not start the install session", e)
            downloader.delete(ready.apkFile)
            _state.value = UpdateState.Failed(UpdateFailure.InstallFailed(PackageInstaller.STATUS_FAILURE, e.message))
            null
        } finally {
            installStarting.set(false)
        }
    }

    /** From [UpdateInstallReceiver]: the final status of a committed session. */
    fun onInstallerStatus(status: Int, message: String?) {
        val installing = _state.value as? UpdateState.Installing ?: return
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            // The user said no in the system dialog: the verified file is still good, offer it again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.ReadyToInstall(installing.update, installing.apkFile)
            else -> {
                scope.launch { downloader.delete(installing.apkFile) }
                UpdateState.Failed(UpdateFailure.InstallFailed(status, message))
            }
        }
    }
}
