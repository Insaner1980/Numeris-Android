package com.emmah.numeris

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.robolectric.RobolectricTestRunner

@RunWith(AndroidHostSuite::class)
class AndroidHostTest

class AndroidHostSuite(
    testClass: Class<*>,
) : Suite(
        testClass,
        listOf(
            ReportPersistenceTest::class.java,
            DesktopPortfolioTest::class.java,
            PerformanceScopeTest::class.java,
            ConnectionIntegrityTest::class.java,
            CloudflareSyncFailureTest::class.java,
            BingScopedSyncTest::class.java,
            ImportContractTest::class.java,
            ImportRecoveryTest::class.java,
            WindowsTransferTest::class.java,
            WindowsConfigurationTest::class.java,
            GoogleAuthorizationResultTest::class.java,
            MobileReportsTest::class.java,
        ).map(::RobolectricTestRunner),
    )
