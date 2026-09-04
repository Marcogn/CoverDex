package com.marcogn.coverdex

import android.app.Application
import com.marcogn.coverdex.data.debug.DebugSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CoverDexApplication : Application() {

    @Inject lateinit var debugSeeder: DebugSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.SEED_DEBUG_DATA) {
            applicationScope.launch { debugSeeder.seed() }
        }
    }
}
