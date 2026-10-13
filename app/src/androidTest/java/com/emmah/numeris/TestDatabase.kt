package com.emmah.numeris

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.NumerisDatabase
import kotlinx.coroutines.Dispatchers

internal fun testDatabase(): NumerisDatabase =
    Room
        .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, NumerisDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
