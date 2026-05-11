package com.xiang.ai.todoentry.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.xiang.ai.todoentry.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthRepository(private val context: Context) {
    private var app: ISingleAccountPublicClientApplication? = null

    suspend fun initialize() {
        if (app != null) return
        app = suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context.applicationContext,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        continuation.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    suspend fun currentAccount(): IAccount? {
        initialize()
        return suspendCancellableCoroutine { continuation ->
            app!!.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    continuation.resume(activeAccount)
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    continuation.resume(currentAccount)
                }

                override fun onError(exception: MsalException) {
                    continuation.resumeWithException(exception)
                }
            })
        }
    }

    suspend fun signIn(activity: Activity): IAccount {
        initialize()
        return suspendCancellableCoroutine { continuation ->
            val parameters = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(SCOPES.toList())
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: com.microsoft.identity.client.IAuthenticationResult) {
                        continuation.resume(authenticationResult.account)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        continuation.resumeWithException(CancellationException("Sign-in was cancelled"))
                    }
                })
                .build()
            app!!.acquireToken(parameters)
        }
    }

    suspend fun signOut() {
        initialize()
        suspendCancellableCoroutine { continuation ->
            app!!.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    continuation.resume(Unit)
                }

                override fun onError(exception: MsalException) {
                    continuation.resumeWithException(exception)
                }
            })
        }
    }

    suspend fun acquireTokenSilent(): String {
        initialize()
        val account = currentAccount() ?: throw IllegalStateException("Please sign in to Microsoft first")
        return suspendCancellableCoroutine { continuation ->
            app!!.acquireTokenSilentAsync(
                SCOPES,
                account.authority,
                object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: com.microsoft.identity.client.IAuthenticationResult) {
                        continuation.resume(authenticationResult.accessToken)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private companion object {
        val SCOPES = arrayOf("User.Read", "Tasks.ReadWrite")
    }
}

class CancellationException(message: String) : RuntimeException(message)
