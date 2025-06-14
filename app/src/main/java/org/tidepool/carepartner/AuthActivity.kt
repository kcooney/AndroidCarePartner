package org.tidepool.carepartner

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData.Companion.authState
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AuthActivity : ComponentActivity() {
    companion object {
        private var INTENT_KEY = "intent"

        fun createPendingIntent(context: Context, postLoginActivity: PendingIntent): PendingIntent {
            val authIntent = Intent(context, AuthActivity::class.java)
            authIntent.putExtra(INTENT_KEY, postLoginActivity)

            return PendingIntent.getActivity(
                context,
                0,
                authIntent,
                PendingIntent.FLAG_MUTABLE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ui = FollowUI()
        setContent {
            LoopFollowTheme {
                ui.App()
                LaunchedEffect(true) {
                    val resp = AuthorizationResponse.fromIntent(intent)
                    val ex = AuthorizationException.fromIntent(intent)
                    if ((resp == null).xor(ex == null)) {
                        authState.update(resp, ex)
                    }
                    if (resp != null) {
                        if (exchangeToken(resp)) {
                            extractPendingIntent(baseContext, getIntent().getExtras()).send()
                            return@LaunchedEffect
                        }
                    }
                    MainActivity.createPendingIntent(baseContext).send()
                }
            }
        }
    }

    private fun extractPendingIntent(context: Context, bundle: Bundle?): PendingIntent {

        fun getParcelable(key: String, bundle: Bundle?): PendingIntent? {
            if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                return bundle?.getParcelable(key, PendingIntent::class.java)
            }
            @Suppress("DEPRECATION")
            return bundle?.getParcelable(key)
        }

        val pendingIntent = getParcelable(INTENT_KEY, bundle)
        return pendingIntent ?: FollowActivity.createPendingIntent(context)
    }

    private suspend fun exchangeToken(resp: AuthorizationResponse): Boolean = suspendCoroutine { continuation ->
        AuthorizationService(baseContext).performTokenRequest(resp.createTokenExchangeRequest()) { resp, ex ->
            authState.update(resp, ex)
            continuation.resume(ex == null)
        }
    }
}