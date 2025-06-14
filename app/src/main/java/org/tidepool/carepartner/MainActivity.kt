package org.tidepool.carepartner

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import org.tidepool.carepartner.backend.PersistentData.Companion.readFromDisk

private const val REAUTH_SERVICE_JOB_ID = 1

private var TAG = MainActivity::class.java.simpleName

class MainActivity : ComponentActivity() {

    companion object {
        fun createPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_MUTABLE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @SuppressLint("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        numRetries = 0
        setContent {
            HomeUI()
            LaunchedEffect(true) {
                baseContext.readFromDisk()
                ReauthService.start(
                    baseContext, REAUTH_SERVICE_JOB_ID,
                    postLoginActivity = FollowActivity.createPendingIntent(baseContext),
                    loginActivity = createPendingIntent(baseContext))
            }
        }
    }
}
