package org.torproject.android.ui.kindness

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import org.torproject.android.Regionalization
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings
import kotlin.time.Duration.Companion.minutes

/**
 * Brings the Snowflake proxy service back when the system has killed it but the
 * user still wants it running (#1799, #1783). START_STICKY alone is not enough:
 * restarts get throttled and eventually abandoned, and some OEMs never deliver
 * them at all, which is how Kindness Mode ends up off until somebody notices.
 */
class KindnessWatchdogJob : JobService() {

    // Returning false from this method means your job is already finished. The system's
    // wakelock for the job will be released, and onStopJob(JobParameters) will not be invoked.
    // aka return false means that the job has completed its work
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob: Prefs.beSnowflakeProxy=${Settings.beSnowflakeProxy}")
        if (!Settings.beSnowflakeProxy) {
            val scheduler =
                applicationContext.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            Log.d(TAG, "onStartJob: cancelling job since pref is false...")
            scheduler.cancel(JOB_ID)
            return false
        }
        Log.d(
            TAG,
            "wantsProxy=${Settings.beSnowflakeProxy} serviceRunning=${SnowflakeProxyService.isRunning}"
        )
        if (shouldRestartKindnessMode(
                wantsProxy = Settings.beSnowflakeProxy,
                serviceRunning = SnowflakeProxyService.isRunning,
                regionBlocked = Regionalization.isKindnessModeDisabledForCountry(Prefs.bridgeCountry)
            )
        ) {
            try {
                Log.d(TAG, "onStartJob: attempting to (re)start kindness in IPtProxy...")
                SnowflakeProxyService.startSnowflakeProxyForegroundService(applicationContext)
            } catch (e: IllegalStateException) {
                // Background foreground-service starts can be denied on API 31+
                // when the app holds no exemption. The next boot, app open, or
                // watchdog run after the user grants one will pick it back up.
                Log.e(TAG, "couldn't start kindness mode $e")
            }
        }
        return false
    }


    // from JobService.java:
    // return false to end the job entirely (or, for a periodic job, to reschedule it according to
    // its requested periodic criteria). Regardless of the value returned, your job must stop executing
    override fun onStopJob(params: JobParameters?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(
                TAG,
                "onStopJob: reason=${params?.stopReason} (AppCanceled=${JobParameters.STOP_REASON_CANCELLED_BY_APP}, timeout=${JobParameters.STOP_REASON_TIMEOUT}...)"
            )
        }
        return false
    }

    companion object {
        private const val JOB_ID = 4817

        // android won't run periodic jobs with periods shorter than 15 min !
        private val PERIODIC_JOB_INTERVAL: Long = 15.minutes.inWholeMilliseconds
        private const val TAG = "KindnessWatchdogJob"

        fun shouldRestartKindnessMode(
            wantsProxy: Boolean,
            serviceRunning: Boolean,
            regionBlocked: Boolean
        ): Boolean {
            return wantsProxy
                    && !serviceRunning
                    && !regionBlocked
        }

        // called when SnowflakeProxyWrapper is constructed
        fun schedulePeriodicKindnessWatchDog(context: Context) {
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobInfoBuilder =
                JobInfo.Builder(JOB_ID, ComponentName(context, KindnessWatchdogJob::class.java))
                    .setPeriodic(PERIODIC_JOB_INTERVAL)
                    .setPersisted(true) // "whether to persist this job across device reboots"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                jobInfoBuilder.setTraceTag(TAG)
            }
            Log.d(TAG, "scheduling $TAG with period of $PERIODIC_JOB_INTERVAL ms")
            jobScheduler.schedule(jobInfoBuilder.build())
        }
    }
}
