package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun resolveDefaultWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return context.getString(resId).trim().takeIf {
        it.isNotEmpty() && !it.startsWith("placeholder", ignoreCase = true)
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit = {},
    onUnauthenticated: () -> Unit = {}
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveDefaultWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val targetContext = context.findActivity() ?: context
            val cred = credentialManager.getCredential(
                request = request,
                context = targetContext
            ).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

suspend fun clearGoogleCredentialState(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
    }
}

fun launchGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit
) {
    scope.launch {
        try {
            val targetContext = context.findActivity() ?: context
            val clientId = resolveDefaultWebClientId(context)
            if (clientId == null) {
                onAuthError("Firebase වින්‍යාසය (google-services.json) සොයාගත නොහැක.")
                return@launch
            }

            val googleIdOption = GetSignInWithGoogleOption.Builder(clientId).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = targetContext
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("වලංගු නොවන Google සහතිකයකි.")
            }
        } catch (e: GetCredentialCancellationException) {
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.e("Auth", "No credential available", e)
            onAuthError("දුරකථනයේ Google ගිණුමක් හමු නොවීය. කරුණාකර ඔබගේ දුරකථනයේ Google ගිණුමකට ලොග් වී නැවත උත්සාහ කරන්න.")
        } catch (e: Exception) {
            Log.e("Auth", "Google sign in error", e)
            val msg = e.localizedMessage ?: "Google හරහා පිවිසීම අසාර්ථක විය."
            onAuthError(msg)
        }
    }
}
