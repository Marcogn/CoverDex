package com.marcogn.coverdex.data.update

import android.content.Context
import android.util.Log
import com.marcogn.coverdex.domain.update.AvailableUpdate
import com.marcogn.coverdex.domain.update.UpdateFailure
import com.marcogn.coverdex.domain.update.sha256FromDigest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

private const val TAG = "ApkDownloader"
private const val UPDATES_DIR = "updates"
private const val CONNECT_TIMEOUT_MS = 15_000
// Per read, not for the whole transfer: a stalled connection fails after this, a slow one doesn't.
private const val READ_TIMEOUT_MS = 30_000
private const val BUFFER_SIZE = 64 * 1024
// Progress is emitted at most once per this many bytes, not per buffer: ~110 events for a 14 MB APK.
private const val PROGRESS_STEP_BYTES = 128 * 1024L

sealed interface DownloadEvent {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : DownloadEvent
    data class Completed(val file: File) : DownloadEvent
}

/**
 * Downloads a release APK into `cacheDir/updates/`. GitHub answers `browser_download_url` with a
 * redirect to its CDN; `HttpURLConnection` follows it on a GET as long as the scheme stays https
 * (it does). The file is written as `.part` and renamed only after the size and, when GitHub gave one,
 * the SHA-256 `digest` match — so a file without `.part` in that folder is always a complete, checked
 * download. A failed, mismatching or cancelled download leaves nothing behind.
 */
@Singleton
class ApkDownloader @Inject constructor(@ApplicationContext private val context: Context) {

    private val updatesDir: File get() = File(context.cacheDir, UPDATES_DIR)

    /**
     * Cold flow: collecting starts the download, cancelling the collector cancels it (checked between
     * buffers). Errors are thrown as [UpdateException].
     */
    fun download(update: AvailableUpdate): Flow<DownloadEvent> = flow {
        val asset = update.apk
        val dir = updatesDir.apply { mkdirs() }
        // Unique per download: a cancelled download's cleanup (its finally block can run up to a read
        // timeout later) must never delete the files of the next download of the same version.
        val target = File(dir, "update-${update.version}-${System.currentTimeMillis()}.apk")
        val part = File(dir, "${target.name}.part")
        val expectedSha256 = sha256FromDigest(asset.digest)
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", UPDATE_USER_AGENT)
        }
        var completed = false
        try {
            val code = networkCall { connection.responseCode }
            if (code !in 200..299) throw UpdateException(UpdateFailure.Http(code))
            val total = asset.sizeBytes.takeIf { it > 0 } ?: connection.contentLengthLong.coerceAtLeast(0)
            emit(DownloadEvent.Progress(0, total))

            var read = 0L
            var lastEmitted = 0L
            networkCall { connection.inputStream }.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = networkCall { input.read(buffer) }
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (read - lastEmitted >= PROGRESS_STEP_BYTES) {
                            emit(DownloadEvent.Progress(read, total))
                            lastEmitted = read
                        }
                    }
                }
            }
            if (asset.sizeBytes > 0 && read != asset.sizeBytes) throw UpdateException(UpdateFailure.SizeMismatch)
            if (expectedSha256 != null && digest.digest().toHex() != expectedSha256) {
                throw UpdateException(UpdateFailure.ChecksumMismatch)
            }
            if (!part.renameTo(target)) throw UpdateException(UpdateFailure.Storage)
            emit(DownloadEvent.Progress(read, total))
            completed = true
            emit(DownloadEvent.Completed(target))
        } finally {
            connection.disconnect()
            // Cancellation lands here too (CancellationException out of ensureActive/emit).
            if (!completed) {
                part.delete()
                target.delete()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Deletes everything in `cacheDir/updates/`. Called once at process start: a file there is either
     * an interrupted download or an APK already handed to the installer (installed or refused) — never
     * something a new process can still use, since the update state lives in memory.
     */
    suspend fun deleteLeftovers() = withContext(Dispatchers.IO) {
        updatesDir.listFiles()?.forEach { file ->
            if (!file.deleteRecursively()) Log.w(TAG, "Could not delete leftover ${file.name}")
        }
    }

    /** Deletes one downloaded APK once it is no longer needed (refused, cancelled, failed). */
    suspend fun delete(file: File) = withContext(Dispatchers.IO) { file.delete() }
}
