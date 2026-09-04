package com.marcogn.coverdex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `ViewModel.viewModelScope` dispatches onto `Dispatchers.Main`, which no JVM/Robolectric test
 * provides by default — without this rule, a coroutine launched inside a ViewModel under test
 * (e.g. its `combine().stateIn(viewModelScope, ...)`) never runs, and `runTest`'s virtual clock
 * hangs waiting for it. [dispatcher] is exposed so a test can pass the *same* scheduler to
 * `runTest(dispatcher)`, keeping the ViewModel's coroutines and the test body on one virtual
 * clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
