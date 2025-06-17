package org.tidepool.carepartner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.authState
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AuthActivity : ComponentActivity() {
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
                            startActivity(Intent(baseContext, FollowActivity::class.java))
                            return@LaunchedEffect
                        }
                    }
                    startActivity(Intent(baseContext, MainActivity::class.java))
                }
            }
        }
    }
    private suspend fun exchangeToken(resp: AuthorizationResponse): Boolean = suspendCoroutine { continuation ->
        AuthorizationService(baseContext).performTokenRequest(resp.createTokenExchangeRequest()) { resp, ex ->
            authState.update(resp, ex)
            continuation.resume(ex == null)
        }
    }
}