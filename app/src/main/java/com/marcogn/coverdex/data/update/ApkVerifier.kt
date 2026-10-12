package com.marcogn.coverdex.data.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.marcogn.coverdex.domain.update.ApkRejection
import com.marcogn.coverdex.domain.update.SignerSet
import com.marcogn.coverdex.domain.update.signersCompatible
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks a downloaded APK before it reaches the installer: same package name, higher `versionCode`,
 * signing certificate compatible with the installed app. Android enforces the last two itself, but
 * only *after* the user confirmed the install and with an opaque `INSTALL_FAILED_…` code; checking
 * here gives a clear reason and never shows a system dialog for an APK that cannot install.
 */
@Singleton
class ApkVerifier @Inject constructor(@ApplicationContext private val context: Context) {

    /** `null` when the APK is acceptable, otherwise why not. */
    suspend fun verify(apk: File): ApkRejection? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val candidate = pm.archiveInfo(apk) ?: return@withContext ApkRejection.UNREADABLE
        val installed = pm.installedInfo(context.packageName)
        when {
            candidate.packageName != installed.packageName -> ApkRejection.DIFFERENT_PACKAGE
            PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed) ->
                ApkRejection.NOT_NEWER
            !signersCompatible(installed.signerSet(), candidate.signerSet()) -> ApkRejection.DIFFERENT_SIGNATURE
            else -> null
        }
    }
}

private fun signatureFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

/**
 * For an *archive*, AOSP's `PackageManager.getPackageArchiveInfo` collects certificates only when
 * `GET_SIGNATURES` is set on Android 9, 10 and 13 (`android-9.0.0_r1`, `android-10.0.0_r1`,
 * `android-13.0.0_r1`); 11 and 14 accept either flag. With `GET_SIGNING_CERTIFICATES` alone
 * `signingInfo` would be null there and every update refused, so both are asked for on API 28+:
 * `signingInfo` is still filled, and it is what [signerSet] reads.
 */
@Suppress("DEPRECATION")
private fun archiveSignatureFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
    } else {
        PackageManager.GET_SIGNATURES
    }

@SuppressLint("PackageManagerGetSignatures")
private fun PackageManager.archiveInfo(apk: File): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(archiveSignatureFlags().toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageArchiveInfo(apk.absolutePath, archiveSignatureFlags())
    }

@SuppressLint("PackageManagerGetSignatures")
private fun PackageManager.installedInfo(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(signatureFlags().toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, signatureFlags())
    }

// PackageManagerGetSignatures (suppressed above) warns about the "fake ID" vulnerability of trusting
// *any one* of several signatures; this avoids it: below API 28 all signatures are compared as a set
// (multipleSigners = true), same as Android's own rule.
private fun PackageInfo.signerSet(): SignerSet {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = signingInfo ?: return SignerSet(emptyList(), multipleSigners = false)
        return if (info.hasMultipleSigners()) {
            SignerSet(info.apkContentsSigners.orEmpty().map { it.sha256() }, multipleSigners = true)
        } else {
            SignerSet(info.signingCertificateHistory.orEmpty().map { it.sha256() }, multipleSigners = false)
        }
    }
    @Suppress("DEPRECATION")
    return SignerSet(signatures.orEmpty().map { it.sha256() }, multipleSigners = true)
}

private fun Signature.sha256(): String = MessageDigest.getInstance("SHA-256").digest(toByteArray()).toHex()
