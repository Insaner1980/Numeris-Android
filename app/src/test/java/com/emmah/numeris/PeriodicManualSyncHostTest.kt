package com.emmah.numeris

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = PeriodicSyncTestApplication::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class PeriodicManualSyncHostTest : PeriodicManualSyncTest()

class PeriodicSyncTestApplication : NumerisApplication() {
    override fun onCreate() {
        // Robolectric leaves this null; WorkManager then treats the app as a secondary process.
        applicationInfo.processName = packageName
        super.onCreate()
    }
}
