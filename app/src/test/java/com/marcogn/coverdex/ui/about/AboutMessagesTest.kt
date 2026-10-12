package com.marcogn.coverdex.ui.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.domain.update.AppVersion
import com.marcogn.coverdex.domain.update.AvailableUpdate
import com.marcogn.coverdex.domain.update.ReleaseAsset
import com.marcogn.coverdex.domain.update.ReleaseInfo
import com.marcogn.coverdex.domain.update.ReleaseNotes
import com.marcogn.coverdex.domain.update.UpdateCheckResult
import com.marcogn.coverdex.domain.update.UpdateFailure
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AboutMessagesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `check results map to a message`() {
        val update = AvailableUpdate(
            AppVersion(1, 1, 0),
            ReleaseInfo("v1.1.0", null, null, "https://example.com", false, false, emptyList()),
            ReleaseAsset("app.apk", "https://example.com/app.apk", 10, null),
            ReleaseNotes(emptyList(), null),
        )
        assertTrue(UpdateCheckResult.Available(update).toMessage(context).contains("1.1.0"))
        assertTrue(UpdateCheckResult.UpToDate.toMessage(context).isNotBlank())
        assertTrue(UpdateCheckResult.NoRelease.toMessage(context).isNotBlank())
        assertTrue(UpdateCheckResult.Failed(UpdateFailure.Http(500)).toMessage(context).contains("500"))
    }
}
