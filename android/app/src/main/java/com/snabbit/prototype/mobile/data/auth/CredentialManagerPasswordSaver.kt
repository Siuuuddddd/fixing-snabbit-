package com.snabbit.prototype.mobile.data.auth

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import com.snabbit.prototype.mobile.domain.auth.PasswordCredentialSaver

class CredentialManagerPasswordSaver(
    private val context: Context,
    private val manager: CredentialManager = CredentialManager.create(context),
) : PasswordCredentialSaver {
    override suspend fun save(email: String, password: String) {
        runCatching { manager.createCredential(context, CreatePasswordRequest(email, password)) }
    }
}

