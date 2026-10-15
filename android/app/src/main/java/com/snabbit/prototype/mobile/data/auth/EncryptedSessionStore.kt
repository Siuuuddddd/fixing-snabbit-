package com.snabbit.prototype.mobile.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class EncryptedSessionStore(context: Context, private val json: Json) : SessionStore {
    private val preferences = EncryptedSharedPreferences.create(
        FILE_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val state = MutableStateFlow(read())

    override val session: Flow<AuthSession?> = state

    override fun current(): AuthSession? = state.value

    override fun save(session: AuthSession) {
        val encoded = json.encodeToString(StoredSession.fromDomain(session))
        check(preferences.edit().putString(SESSION, encoded).commit()) { "Unable to persist session" }
        state.value = session
    }

    override fun clear() {
        check(preferences.edit().remove(SESSION).commit()) { "Unable to clear session" }
        state.value = null
    }

    private fun read(): AuthSession? = preferences.getString(SESSION, null)?.let { encoded ->
        runCatching { json.decodeFromString<StoredSession>(encoded).toDomain() }
            .onFailure { preferences.edit().remove(SESSION).commit() }
            .getOrNull()
    }

    private companion object {
        const val FILE_NAME = "snabbit_secure_auth"
        const val SESSION = "session_v2"
    }
}

@Serializable
private data class StoredSession(
    val accessToken: String,
    val tokenType: String,
    val expiresAtEpochSeconds: Long,
    val refreshToken: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val pictureUrl: String?,
    val isNewUser: Boolean,
    val role: String,
    val phoneNumber: String? = null,
) {
    fun toDomain() = AuthSession(
        accessToken,
        tokenType,
        expiresAtEpochSeconds,
        refreshToken,
        SessionUser(userId, userName, userEmail, pictureUrl, isNewUser, role, phoneNumber),
    )

    companion object {
        fun fromDomain(session: AuthSession) = StoredSession(
            session.accessToken,
            session.tokenType,
            session.expiresAtEpochSeconds,
            session.refreshToken,
            session.user.id,
            session.user.name,
            session.user.email,
            session.user.pictureUrl,
            session.user.isNewUser,
            session.user.role,
            session.user.phoneNumber,
        )
    }
}
