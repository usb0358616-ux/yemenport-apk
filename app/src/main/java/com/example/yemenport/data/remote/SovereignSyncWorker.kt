package com.example.yemenport.data.remote

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yemenport.data.local.AppDatabase
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Offline-First WorkManager worker that queues and retries pending Room database records
 * (travelers, travel crossings, reports, seizures, patrols, audit logs) whenever network
 * connectivity becomes available.
 */
class SovereignSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "Skipping background cloud sync: no authenticated officer session")
            return Result.success()
        }

        return try {
            val dao = AppDatabase.getInstance(applicationContext).borderDao()
            val syncRepo = FirestoreSyncRepository(applicationContext)
            val pushRes = syncRepo.pushPendingToCloud(currentUser.uid, dao)
            val pullRes = syncRepo.pullCloudIntoLocal(currentUser.uid, dao)

            if (pushRes.isSuccess && pullRes.isSuccess) {
                Log.i(
                    TAG,
                    "WorkManager sync complete: pushed=${pushRes.getOrDefault(0)}, pulled=${pullRes.getOrDefault(0)}"
                )
                Result.success()
            } else {
                Log.w(TAG, "WorkManager sync partial/offline failure; scheduling retry")
                Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager sync encountered transient exception; retrying", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "SovereignSyncWorker"
    }
}

object SovereignSyncScheduler {
    private const val IMMEDIATE_SYNC_WORK_NAME = "yemenport_immediate_cloud_sync"
    private const val PERIODIC_SYNC_WORK_NAME = "yemenport_periodic_cloud_sync"

    private fun connectedConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /**
     * Queues a one-shot sync job that waits until internet connectivity is available
     * and backs off exponentially on transient failures, preserving the 100% offline-first flow.
     */
    fun enqueueImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SovereignSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    /**
     * Registers a periodic 15-minute background sync while connected so queued audit logs
     * and offline crossings upload reliably without officer intervention.
     */
    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SovereignSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }
}
