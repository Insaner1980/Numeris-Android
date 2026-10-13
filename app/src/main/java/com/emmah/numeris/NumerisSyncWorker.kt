package com.emmah.numeris

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.emmah.numeris.core.model.SyncState
import java.time.Duration

class NumerisSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val container: AppContainer,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        container.importRepository.recoverInterruptedTransfer()
        return when (container.syncOrchestrator.syncEverything().state) {
            is SyncState.Success, is SyncState.PartialSuccess -> Result.success()
            is SyncState.AuthRequired -> Result.failure()
            else -> Result.retry()
        }
    }
}

class NumerisWorkerFactory(
    private val container: () -> AppContainer,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == NumerisSyncWorker::class.java.name) {
            NumerisSyncWorker(appContext, workerParameters, container())
        } else {
            null
        }
}

object PeriodicSyncScheduler {
    private const val WORK_NAME = "numeris.periodic-sync"

    fun schedule(
        context: Context,
        intervalHours: Int = DEFAULT_INTERVAL_HOURS.toInt(),
    ) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        val request =
            PeriodicWorkRequest
                .Builder(NumerisSyncWorker::class.java, Duration.ofHours(intervalHours.coerceIn(1, 168).toLong()))
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    Duration.ofMinutes(INITIAL_BACKOFF_MINUTES),
                ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

    private const val DEFAULT_INTERVAL_HOURS = 12L
    private const val INITIAL_BACKOFF_MINUTES = 15L
}
