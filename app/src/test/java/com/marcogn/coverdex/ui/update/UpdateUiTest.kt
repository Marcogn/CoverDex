package com.marcogn.coverdex.ui.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcogn.coverdex.data.settings.SettingsPreferences
import com.marcogn.coverdex.data.settings.settingsDataStore
import com.marcogn.coverdex.data.update.ApkDownloader
import com.marcogn.coverdex.data.update.ApkInstaller
import com.marcogn.coverdex.data.update.ApkVerifier
import com.marcogn.coverdex.data.update.GitHubReleaseClient
import com.marcogn.coverdex.data.update.UpdateManager
import com.marcogn.coverdex.domain.update.ApkRejection
import com.marcogn.coverdex.domain.update.UnusableReleaseReason
import com.marcogn.coverdex.domain.update.UpdateFailure
import com.marcogn.coverdex.domain.update.UpdateFrequency
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class UpdateUiTest {

    @get:Rule
    val mainDispatcherRule = com.marcogn.coverdex.MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = SettingsPreferences(context)
    private val installer = ApkInstaller(context)
    private val manager = UpdateManager(
        GitHubReleaseClient(), ApkDownloader(context), ApkVerifier(context), installer, preferences,
    )

    // Same reason as SettingsPreferencesTest: the on-disk DataStore survives between test methods.
    @Before
    fun clearPersistedState(): Unit = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @Test
    fun `every failure maps to a non-blank message`() {
        val failures = listOf<UpdateFailure>(
            UpdateFailure.Network("x"), UpdateFailure.RateLimited, UpdateFailure.Http(500),
            UpdateFailure.InvalidResponse(null), UpdateFailure.Storage, UpdateFailure.SizeMismatch,
            UpdateFailure.ChecksumMismatch, UpdateFailure.InstallFailed(1, null), UpdateFailure.InstallFailed(1, "boom"),
        ) + UnusableReleaseReason.entries.map { UpdateFailure.UnusableRelease(it) } +
            ApkRejection.entries.map { UpdateFailure.VerificationFailed(it) }
        failures.forEach { assertTrue("$it", it.toMessage(context).isNotBlank()) }
    }

    @Test
    fun `http and install detail end up in the message`() {
        assertTrue(UpdateFailure.Http(503).toMessage(context).contains("503"))
        assertTrue(UpdateFailure.InstallFailed(1, "boom").toMessage(context).contains("boom"))
    }

    @Test
    fun `different signature gets its own reason`() {
        val signature = UpdateFailure.VerificationFailed(ApkRejection.DIFFERENT_SIGNATURE).toMessage(context)
        val notNewer = UpdateFailure.VerificationFailed(ApkRejection.NOT_NEWER).toMessage(context)
        assertTrue(signature != notNewer)
    }

    @Test
    fun `frequency defaults to weekly and the selection is persisted`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = UpdateViewModel(manager, installer, preferences)
        assertEquals(UpdateFrequency.WEEKLY, viewModel.frequency.first { true })

        UpdateFrequency.entries.forEach { option ->
            viewModel.onFrequencySelected(option)
            assertEquals(option, viewModel.frequency.first { it == option })
            assertEquals(option, preferences.updateFrequency.first())
            // A new ViewModel (screen reopened, process restarted) reads the stored value back.
            assertEquals(option, UpdateViewModel(manager, installer, preferences).frequency.first { it == option })
        }
    }
}
