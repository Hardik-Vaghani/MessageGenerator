package com.example.messagegenerator.presentation

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Telephony
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MessageObserverJobService : JobService() {

    private val TAG = "BASE_TAG" + MessageObserverJobService::class.java
    private var job: Job? = null
    private var jobParams: JobParameters? = null

    companion object {
        private const val MEDIA_OBSERVER_JOB_ID = 999
        private var JOB_INFO: JobInfo? = null

        fun scheduleJob(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)

            if (JOB_INFO != null) {
                reSchedule(context)
            }
            else {
                val builder = JobInfo.Builder(
                    MEDIA_OBSERVER_JOB_ID,
                    ComponentName(context, MessageObserverJobService::class.java)
                )

                builder.addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        Telephony.Sms.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )

                builder.setTriggerContentMaxDelay(500)
                JOB_INFO = builder.build()
                js.schedule(JOB_INFO!!)
            }
        }

        private fun reSchedule(context: Context): Int {
            return (context.getSystemService(JobScheduler::class.java) as JobScheduler)
                .schedule(JOB_INFO!!)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartJob(params: JobParameters?): Boolean {
        jobParams = params
        job = CoroutineScope(Dispatchers.IO).launch {

            jobFinished(params, false)
            scheduleJob(this@MessageObserverJobService)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        job?.cancel()
        return true
    }

}