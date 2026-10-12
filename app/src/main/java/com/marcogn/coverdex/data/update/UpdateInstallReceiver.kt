package com.marcogn.coverdex.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "UpdateInstallReceiver"

/**
 * Receives the `PackageInstaller` session status ([ApkInstaller.statusPendingIntent], explicit,
 * not exported). `STATUS_PENDING_USER_ACTION` carries the system confirmation activity, started here;
 * this happens right after the user tapped "Install", with the app in the foreground, which is what
 * lets a receiver start an activity under Android 10+'s background-start limits. Every other status
 * goes to [UpdateManager.onInstallerStatus]. On success the system kills this process to replace the
 * app, so `STATUS_SUCCESS` is usually never seen.
 */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var updateManager: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // IntentCompat, not the typed overload added in API 33: IntentCompat uses it only from API 34,
            // because of a platform bug in it on 33 that could lose the confirmation intent.
            val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirmation != null) {
                try {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Cannot show the install confirmation", e)
                }
            }
            // The session would otherwise wait forever for a confirmation nobody can give.
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            if (sessionId != -1) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            updateManager.onInstallerStatus(PackageInstaller.STATUS_FAILURE, message)
            return
        }
        updateManager.onInstallerStatus(status, message)
    }
}
