package com.iproxy.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iproxy.app.core.util.CoreLogBuffer
import com.iproxy.app.data.repository.AppRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager 后台定时更新订阅任务
 */
class SubscriptionSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = AppRepository(applicationContext)
        val subs = repo.storage.readSubscriptionsSync().filter { it.autoUpdateEnabled }
        if (subs.isEmpty()) return Result.success()

        var successCount = 0
        for (sub in subs) {
            repo.refreshSubscription(sub.id).onSuccess { successCount++ }
        }
        CoreLogBuffer.i("SyncWorker", "后台定时订阅同步完成，成功更新 $successCount 个订阅节点")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "i_subscription_periodic_sync"

        fun schedule(context: Context, intervalHours: Long = 12L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SubscriptionSyncWorker>(
                intervalHours.coerceAtLeast(1L),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
