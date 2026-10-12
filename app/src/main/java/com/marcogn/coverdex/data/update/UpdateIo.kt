package com.marcogn.coverdex.data.update

import com.marcogn.coverdex.BuildConfig
import com.marcogn.coverdex.domain.update.UpdateFailure
import java.io.IOException

/**
 * GitHub's REST API rejects requests without a User-Agent and asks for the app's name in it (REST
 * docs, "User agent required") — the opposite of the desktop-browser User-Agent the TheGamesDB and
 * HowLongToBeat clients need against anti-bot filtering. Used for both the API call and the download.
 */
internal val UPDATE_USER_AGENT = "${BuildConfig.UPDATE_GITHUB_REPO}/${BuildConfig.VERSION_NAME} (Android)"

/** Carries an [UpdateFailure] out of the data layer; `UpdateManager` turns it into state. */
class UpdateException(val failure: UpdateFailure, cause: Throwable? = null) :
    Exception(failure.toString(), cause)

/** Runs one blocking network step, turning an `IOException` into [UpdateFailure.Network]. */
internal inline fun <T> networkCall(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw UpdateException(UpdateFailure.Network(e.message), e)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
