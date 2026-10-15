package com.snabbit.prototype.mobile.data.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.GoogleIdToken
import com.snabbit.prototype.mobile.domain.auth.GoogleIdTokenProvider
import java.security.SecureRandom
import java.util.Base64

class CredentialManagerGoogleIdTokenProvider(
    private val activity: Activity,
    private val serverClientId: String,
    private val credentialManager: CredentialManager = CredentialManager.create(activity),
) : GoogleIdTokenProvider {
    override suspend fun requestIdToken(): GoogleIdToken {
        if (serverClientId.isBlank()) throw AuthFailure(AuthFailureReason.NOT_CONFIGURED)
        val nonce = randomNonce()
        val option = GetSignInWithGoogleOption.Builder(serverClientId).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw AuthFailure(AuthFailureReason.UNKNOWN)
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleIdToken(google.idToken, nonce)
        } catch (failure: GetCredentialCancellationException) {
            throw AuthFailure(AuthFailureReason.CANCELLED)
        } catch (failure: NoCredentialException) {
            throw AuthFailure(AuthFailureReason.NO_GOOGLE_ACCOUNT)
        } catch (failure: GoogleIdTokenParsingException) {
            throw AuthFailure(AuthFailureReason.GOOGLE_CONFIGURATION, failure.message)
        } catch (failure: GetCredentialException) {
            throw AuthFailure(AuthFailureReason.PLAY_SERVICES_UNAVAILABLE, failure.message)
        }
    }

    override suspend fun clearCredentialState() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object { const val NONCE_BYTES = 32 }
}
