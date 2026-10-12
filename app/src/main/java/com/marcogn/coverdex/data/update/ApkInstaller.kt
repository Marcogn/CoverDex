package com.marcogn.coverdex.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The outcome of [ApkInstaller.install] as far as the app can see it before the system takes over. */
sealed interface InstallStart {
    /**
     * "Install unknown apps" is off for this app. The UI launches [settingsIntent] (the per-app toggle,
     * `ACTION_MANAGE_UNKNOWN_APP_SOURCES`) and, back from it, calls `UpdateManager.install()` again.
     */
    data class PermissionRequired(val settingsIntent: Intent) : InstallStart

    /** Session committed: [UpdateInstallReceiver] gets the status, the system shows its confirmation. */
    data object Committed : InstallStart
}

/**
 * Hands a verified APK to the system installer through a `PackageInstaller` session: no
 * `FileProvider`, no `ACTION_VIEW`/`ACTION_INSTALL_PACKAGE` (deprecated since API 29). The user always
 * confirms in the system dialog (`STATUS_PENDING_USER_ACTION`, started by [UpdateInstallReceiver]):
 * a silent install is out of scope, and its conditions change between Android versions.
 */
@Singleton
class ApkInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Per-app "install unknown apps" (API 26+, requires `REQUEST_INSTALL_PACKAGES`). Below API 26 there
     * is no per-app permission (`canRequestPackageInstalls()` does not exist): the global "Unknown
     * sources" setting applies and the system installer itself refuses or asks, so this reports true.
     */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    suspend fun install(apk: File): InstallStart {
        if (!canInstall()) return InstallStart.PermissionRequired(unknownSourcesSettingsIntent())
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Already the effective default for an installer holding REQUEST_INSTALL_PACKAGES
                    // (SessionParams.setRequireUserAction docs); stated explicitly because "always
                    // with the user's confirmation" is a product decision, not an accident of defaults.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    session.commit(statusPendingIntent(sessionId).intentSender)
                }
            } catch (e: Exception) {
                // Also when openSession() itself fails: a created session left behind keeps its staged
                // storage and counts against the per-app session limit.
                runCatching { installer.abandonSession(sessionId) }
                throw e
            }
        }
        return InstallStart.Committed
    }

    /**
     * Explicit broadcast to [UpdateInstallReceiver]. Mutable is required: the installer adds
     * `EXTRA_STATUS`/`EXTRA_INTENT` to it (FLAG_MUTABLE is needed for that from API 31); explicit
     * component, so nobody else can redirect it.
     */
    private fun statusPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}
