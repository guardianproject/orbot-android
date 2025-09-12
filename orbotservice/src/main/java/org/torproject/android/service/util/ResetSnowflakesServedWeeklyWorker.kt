package org.torproject.android.service.util

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ResetSnowflakesServedWeeklyWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        Prefs.resetSnowflakesServedWeekly()
        return Result.success()
    }
}