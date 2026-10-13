package org.torproject.android.ui.kindness

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import org.torproject.android.util.Prefs
import java.util.concurrent.Executors

class SnowflakeProxyJobScheduler : JobService() {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun onStartJob(params: JobParameters?): Boolean {
        executor.execute {
            val shouldReschedule = Prefs.beSnowflakeProxy
            if (shouldReschedule) {
                Log.d(TAG, "starting up snowflake proxy service if it's not already running")
                SnowflakeProxyService.startSnowflakeProxyForegroundService(this)
            }
            jobFinished(params, shouldReschedule)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "job stopped before finished, not restarting...")
        return false
    }

    companion object {
        const val TAG = "SnowflakeProxyJobScheduler"
        const val JOB_ID = 3432
        const val THIRTY_MINUTES = 30 * 60 * 1000L

        // starts SnowflakeProxyService restart task if kindness mode is activated, job isn't rescheduled if the
        // user cancels kindness mode
        fun attemptToStartSnowflakeProxyJobScheduler(context: Context) {
            if (!Prefs.beSnowflakeProxy) return
            val component = ComponentName(context, SnowflakeProxyJobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, component)
                // SnowflakeProxyService handles network changes and calls IPtProxy accordingly...
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(THIRTY_MINUTES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                job.setTraceTag(TAG)

            val scheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val result = scheduler.schedule(job.build())
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Scheduled job ID $JOB_ID")
            } else  {
                Log.wtf(TAG, "unable to schedule job $JOB_ID")
            }
        }

        fun cancelSnowflakeProxyJobScheduler(context: Context) {
            Log.d(TAG, "cancelling job $JOB_ID")
            val scheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            scheduler.cancel(JOB_ID)
        }
    }

}