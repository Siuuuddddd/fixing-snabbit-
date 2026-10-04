package com.snabbit.prototype.mobile.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class EncryptedTokenStore(context: Context) : TokenStore {
    private val preferences = EncryptedSharedPreferences.create(
        FILE_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun accessToken(): String? = preferences.getString(ACCESS_TOKEN, null)

    override fun saveAccessToken(token: String) {
        preferences.edit().putString(ACCESS_TOKEN, token).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "snabbit_secure_auth"
        const val ACCESS_TOKEN = "access_token"
    }
}
