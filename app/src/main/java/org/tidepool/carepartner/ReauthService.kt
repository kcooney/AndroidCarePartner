package org.tidepool.carepartner

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData

private var TAG = ReauthService::class.java.simpleName

private const val PERIOD_MILLIS = 5_000L

/** Periodically refreshes the access token when needed. */
class ReauthService : JobService() {

    companion object {
        private lateinit var loginActivity: PendingIntent

        /**
         * Starts the service.
         *
         * @param context The Context in which this PendingIntent should start
         * the activity.
         * @param jobId Application-provided id for the job. This ID must be unique across
         * all clients of the same uid (not just the same package).  You will want to make sure
         * this is a stable ID across app updates, so probably not based on a resource ID.
         * @param postLoginActivity Activity to start once the user is authorized.
         * @param loginActivity Activity to start to allow the user to login.
         */
        fun start(context: Context, jobId: Int, postLoginActivity: PendingIntent, loginActivity: PendingIntent) {
            this.loginActivity = loginActivity
            schedule(context, jobId)

            if (PersistentData.hasRefreshToken) {
                sendRefreshAccessTokenRequestIfNeeded(context) { ex ->
                    if (ex != null) {
                        Log.w(TAG, ex)
                    } else {
                        // We either had a valid token, or we just created one.
                        postLoginActivity.send()
                    }
                }
            }
        }

        /**
         * Directs the user to login.
         *
         * @param context The Context in which this PendingIntent should start
         * the activity.
         * @param postLoginActivity Activity to start once the user is authorized.
         */
        fun login(context: Context, postLoginActivity: PendingIntent) {
            AuthorizationService(context).performAuthorizationRequest(
                PersistentData.getAuthRequestBuilder().build(),
                AuthActivity.createPendingIntent(context, postLoginActivity),
                loginActivity)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.v(TAG, "Starting")

        if (!PersistentData.hasRefreshToken) {
            Log.w(TAG, "No fresh token; cannot refresh access token")
            return false
        }

        sendRefreshAccessTokenRequestIfNeeded(this) { ex ->
            if (ex != null) {
                jobFinished(params, true)  // Request exponential retry
            } else {
                jobFinished(params, false) // Job done; reschedule
                schedule(applicationContext, params.jobId)
            }
        }
        return true // The job is not done yet; we will call jobFinished() when it is.
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Log.v(TAG, "Stopping")
        return true
    }
}

class NoCredentialsException: RuntimeException("No Credentials")

/** If there is an auth token refreshes the access token if it has expired. */
private fun sendRefreshAccessTokenRequestIfNeeded(context: Context, callback: (ex: Exception?) -> Unit) {
    if (PersistentData.authState.refreshToken == null) {
        callback(NoCredentialsException())
    } else if (PersistentData.authState.needsTokenRefresh) {
        Log.v(TAG, "Performing a token refresh to get a new access token")
        val request = PersistentData.authState.createTokenRefreshRequest()
        AuthorizationService(context).performTokenRequest(request) { resp, ex ->
            PersistentData.authState.update(resp, ex)
            callback(ex)
        }
    } else {
        callback(null)
    }
}

private fun schedule(context: Context, jobId: Int) {
    val serviceComponent = ComponentName(context, ReauthService::class.java)
    val builder = JobInfo.Builder(jobId, serviceComponent)
        .setPeriodic(PERIOD_MILLIS)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)

    val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    jobScheduler.schedule(builder.build())
}

fun Context.login(postLoginActivity: PendingIntent) {
    ReauthService.login(this, postLoginActivity)
}
