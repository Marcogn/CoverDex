package com.marcogn.coverdex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * Holds the ViewModels a test creates in a real [ViewModelStore], so [clear] cancels their
 * `viewModelScope` the way a device does when the screen goes away. A test that owns a Room
 * database calls [clear] before `database.close()`: otherwise a ViewModel's still-collecting Room
 * flow can re-query on Room's executor after the close, which Room 2.8 fails with
 * "connection is closed", reported against whichever test is running.
 */
class TrackedViewModels {
    private val store = ViewModelStore()
    private var created = 0

    fun <T : ViewModel> add(viewModelClass: Class<T>, create: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }
        return ViewModelProvider(store, factory)["viewModel${created++}", viewModelClass]
    }

    fun clear() = store.clear()
}
